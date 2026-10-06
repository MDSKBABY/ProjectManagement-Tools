CREATE TABLE daily_report_meeting_record (
    daily_report_id BIGINT NOT NULL REFERENCES daily_report (id) ON DELETE CASCADE,
    meeting_record_id BIGINT NOT NULL REFERENCES meeting_record (id),
    PRIMARY KEY (daily_report_id, meeting_record_id)
);

CREATE INDEX idx_daily_report_meeting_record_meeting
    ON daily_report_meeting_record (meeting_record_id);
