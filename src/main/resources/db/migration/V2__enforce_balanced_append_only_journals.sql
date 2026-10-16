CREATE FUNCTION verify_journal_balance()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
DECLARE
target_id UUID;
    entry_count BIGINT;
    net_amount NUMERIC;
BEGIN
    IF TG_TABLE_NAME = 'journal_transactions' THEN
        target_id := NEW.id;
ELSE
        target_id := NEW.journal_id;
END IF;

SELECT
    COUNT(*),
    COALESCE(
            SUM(
                    CASE
                        WHEN direction = 'DEBIT' THEN amount_minor::NUMERIC
                    ELSE -amount_minor::NUMERIC
                    END
            ),
            0
    )
INTO entry_count, net_amount
FROM journal_entries
WHERE journal_id = target_id;

IF entry_count < 2 OR net_amount <> 0 THEN
        RAISE EXCEPTION 'Journal % must contain balanced entries', target_id
            USING ERRCODE = '23514';
END IF;

RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER journal_header_balance_check
AFTER INSERT ON journal_transactions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION verify_journal_balance();

CREATE CONSTRAINT TRIGGER journal_entry_balance_check
AFTER INSERT ON journal_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION verify_journal_balance();

CREATE FUNCTION reject_financial_record_mutation()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Financial records are append-only'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER journal_transactions_append_only
    BEFORE UPDATE OR DELETE ON journal_transactions
FOR EACH ROW
EXECUTE FUNCTION reject_financial_record_mutation();

CREATE TRIGGER journal_entries_append_only
    BEFORE UPDATE OR DELETE ON journal_entries
FOR EACH ROW
EXECUTE FUNCTION reject_financial_record_mutation();

CREATE TRIGGER transfers_append_only
    BEFORE UPDATE OR DELETE ON transfers
FOR EACH ROW
EXECUTE FUNCTION reject_financial_record_mutation();