UPDATE job_template
SET title = '销售岗面试'
WHERE user_id IS NOT NULL
  AND track_id = (SELECT id FROM interview_track WHERE code = 'sales');
