-- @ElementCollection on Schedule.weekDays (DayOfWeek as STRING)
CREATE TABLE schedule_weekdays (
    schedule_id BIGINT NOT NULL REFERENCES schedules (id) ON DELETE CASCADE,
    day_of_week VARCHAR(16) NOT NULL,
    PRIMARY KEY (schedule_id, day_of_week)
);
