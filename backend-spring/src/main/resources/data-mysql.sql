INSERT INTO college (id, school_name, name, contact_name, contact_phone, description) VALUES
('college-001', '示范大学', '教育学院', '王主任', '13800000001', '一期演示学院'),
('college-002', '示范大学', '文学院', '刘主任', '13800000011', '预置学院，等待分配管理员账号'),
('college-003', '示范大学', '外国语学院', '陈主任', '13800000012', '预置学院，等待分配管理员账号');

INSERT INTO user_account (id, account, name, role, password, must_change_password, status, college_id, last_login_at) VALUES
('user-super-001', 'root', '平台管理员', 'SUPER_ADMIN', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 0, 'ACTIVE', NULL, NULL),
('user-college-001', 'college01', '教育学院管理员', 'COLLEGE_ADMIN', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 0, 'ACTIVE', 'college-001', NULL),
('user-teacher-001', 'T1001', '李老师', 'TEACHER', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 0, 'ACTIVE', 'college-001', NULL),
('user-teacher-002', 'T1002', '周老师', 'TEACHER', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 0, 'ACTIVE', 'college-001', NULL),
('user-student-001', '20230001', '张三', 'STUDENT', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 0, 'ACTIVE', 'college-001', NULL),
('user-student-002', '20230002', '李四', 'STUDENT', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 0, 'ACTIVE', 'college-001', NULL);

INSERT INTO teacher (id, user_id, name, employee_no, college_id, department, phone, status) VALUES
('teacher-001', 'user-teacher-001', '李老师', 'T1001', 'college-001', '语文教研室', '13900000001', 'ACTIVE'),
('teacher-002', 'user-teacher-002', '周老师', 'T1002', 'college-001', '班主任教研室', '13900000002', 'ACTIVE');

INSERT INTO student (id, user_id, name, student_no, college_id, major, class_name, phone, internship_type, internship_status, profile_completed) VALUES
('student-001', 'user-student-001', '张三', '20230001', 'college-001', '汉语言文学', '2023级1班', '13700000001', 'TEACHING', '实习中', 1),
('student-002', 'user-student-002', '李四', '20230002', 'college-001', '教育学', '2023级2班', '13700000002', 'HEAD_TEACHER', '待申请', 1);

INSERT INTO organization (id, college_id, name, address, contact_name, contact_phone, nature, cooperation_status) VALUES
('org-001', 'college-001', '示范附属中学', '武汉市洪山区书香路1号', '刘主任', '13600000001', '公办中学', '优质合作'),
('org-002', 'college-001', '明德实验小学', '武汉市江夏区知行路2号', '陈主任', '13600000002', '公办小学', '合作中');

INSERT INTO mentor_application (id, student_id, teacher_id, status, student_remark, teacher_remark, college_remark, created_at, teacher_reviewed_at, college_reviewed_at, effective_at) VALUES
('mentor-app-001', 'student-001', 'teacher-001', '已生效', '申请李老师担任指导教师', '同意接收', '学院复核通过', '2026-03-01 09:00:00', '2026-03-02 09:00:00', '2026-03-03 09:00:00', '2026-03-03 09:00:00'),
('mentor-app-002', 'student-002', 'teacher-002', '待教师确认', '申请周老师担任指导教师', '', '', '2026-03-10 09:00:00', NULL, NULL, NULL);

INSERT INTO internship_application (id, student_id, organization_id, status, batch_name, position, grade_target, start_date, end_date, remark, attachments_json, organization_confirmation, organization_feedback, received_at, created_at, reviewed_at, review_comment) VALUES
('internship-app-001', 'student-001', 'org-001', '已通过', '2026春季批次', '初中语文', '七年级', '2026-03-05', '2026-06-30', '已与学校沟通完成', '[{"id":"file-001","name":"实习申请表.pdf","url":"#"}]', '已确认接收', '欢迎实习，按时到岗', '2026-03-04', '2026-03-03 08:00:00', '2026-03-04 08:00:00', '指导关系已生效，可进入实习'),
('internship-app-002', 'student-002', 'org-002', '待学院审批', '2026春季批次', '班主任助理', '小学五年级', '2026-03-20', '2026-06-30', '希望从事班主任实习', '[]', '待登记', '', NULL, '2026-03-12 08:00:00', NULL, '');

INSERT INTO form_template (code, name, category, description, applicable_types_json, field_schema_json, enabled, sort_no, created_at, updated_at) VALUES
('internship-journal', '实习记录', 'COMMON', '记录每周实习内容与反思。', '["TEACHING","HEAD_TEACHER"]', '[{"key":"title","label":"记录标题","type":"text","required":true,"placeholder":"请输入本周记录标题"},{"key":"summary","label":"实习内容摘要","type":"textarea","required":true,"placeholder":"请输入本周完成情况与反思"}]', 1, 10, '2026-03-01 08:00:00', '2026-03-01 08:00:00'),
('internship-reflection', '实习心得', 'COMMON', '沉淀阶段性实习体会与改进计划。', '["TEACHING","HEAD_TEACHER"]', '[{"key":"title","label":"心得主题","type":"text","required":true,"placeholder":"请输入心得主题"},{"key":"summary","label":"心得内容","type":"textarea","required":true,"placeholder":"请输入实习心得与成长反思"}]', 1, 20, '2026-03-01 08:10:00', '2026-03-01 08:10:00'),
('lecture-record', '听课记录', 'TEACHING', '用于记录示范课、听评课观察要点。', '["TEACHING"]', '[{"key":"title","label":"课程标题","type":"text","required":true,"placeholder":"请输入听课课程标题"},{"key":"summary","label":"课堂观察摘要","type":"textarea","required":true,"placeholder":"请输入导入、互动、提问等观察内容"}]', 1, 30, '2026-03-01 08:20:00', '2026-03-01 08:20:00'),
('teaching-clock-in', '教学实习打卡', 'TEACHING', '记录教学实习到岗、课堂与作业辅导情况。', '["TEACHING"]', '[{"key":"title","label":"打卡主题","type":"text","required":true,"placeholder":"请输入今日打卡主题"},{"key":"summary","label":"到岗与教学情况","type":"textarea","required":true,"placeholder":"请输入到岗、授课或作业辅导情况"}]', 1, 40, '2026-03-01 08:30:00', '2026-03-01 08:30:00'),
('class-duty-record', '值守记录', 'HEAD_TEACHER', '记录班主任值日、班级巡查和学生管理。', '["HEAD_TEACHER"]', '[{"key":"title","label":"值守主题","type":"text","required":true,"placeholder":"请输入值守主题"},{"key":"summary","label":"班级管理摘要","type":"textarea","required":true,"placeholder":"请输入班级巡查、纪律与沟通情况"}]', 1, 50, '2026-03-01 08:40:00', '2026-03-01 08:40:00'),
('class-meeting-plan', '班会方案', 'HEAD_TEACHER', '用于提交班会设计与组织实施方案。', '["HEAD_TEACHER"]', '[{"key":"title","label":"班会主题","type":"text","required":true,"placeholder":"请输入班会主题"},{"key":"summary","label":"班会方案摘要","type":"textarea","required":true,"placeholder":"请输入班会目标、流程与组织要点"}]', 1, 60, '2026-03-01 08:50:00', '2026-03-01 08:50:00');

INSERT INTO form_instance (id, student_id, template_code, template_name, category, status, version_no, content_json, attachments_json, teacher_comment, college_comment, score, created_at, updated_at, submitted_at, teacher_reviewed_at, college_reviewed_at, history_json) VALUES
('form-001', 'student-001', 'internship-journal', '实习记录', 'COMMON', '已归档', 1, '{"title":"第一周实习记录","summary":"完成听课、备课与课堂观察。"}', '[]', '记录详实', '同意归档', 92, '2026-03-06 08:00:00', '2026-03-08 08:00:00', '2026-03-06 08:00:00', '2026-03-07 08:00:00', '2026-03-08 08:00:00', '[]'),
('form-002', 'student-001', 'lecture-record', '听课记录', 'TEACHING', '教师审核中', 1, '{"title":"语文示范课听课记录","summary":"观察课堂导入和提问设计。"}', '[]', '', '', NULL, '2026-03-10 08:00:00', '2026-03-10 08:00:00', '2026-03-10 08:00:00', NULL, NULL, '[]'),
('form-003', 'student-002', 'class-duty-record', '值守记录', 'HEAD_TEACHER', '草稿', 1, '{"title":"班主任值守记录草稿","summary":"准备提交。"}', '[]', '', '', NULL, '2026-03-15 08:00:00', '2026-03-15 08:00:00', NULL, NULL, NULL, '[]');

INSERT INTO message_notice (id, user_id, type, title, content, link, read_flag, created_at) VALUES
('message-001', 'user-student-001', '待办提醒', '听课记录待教师审核', '你提交的《听课记录》已进入教师审核中。', '/student/forms/teaching', 0, '2026-03-10 08:10:00'),
('message-002', 'user-teacher-001', '待办提醒', '张三提交了新的听课记录', '请在规定时间内完成审核。', '/teacher/reviews', 0, '2026-03-10 08:10:00'),
('message-003', 'user-college-001', '待办提醒', '李四的实习申请待审批', '请核验指导关系并登记单位确认结果。', '/college/internship-applications', 0, '2026-03-12 08:10:00');

INSERT INTO guidance_record (id, teacher_id, student_id, guidance_at, mode, problem, advice, follow_up) VALUES
('guide-001', 'teacher-001', 'student-001', '2026-03-09 13:00:00', '线上', '导入环节节奏偏慢', '增加情境提问和板书提示', '下次试讲继续跟进');

INSERT INTO evaluation_record (id, teacher_id, student_id, stage_comment, summary_comment, final_score, dimension_scores_json, strengths_comment, improvement_comment, college_comment, college_score, submitted_to_college, confirmed_by_college, returned_by_college, evaluated_at, college_returned_at, college_confirmed_at) VALUES
('eval-001', 'teacher-001', 'student-001', '课堂组织较为稳定', '具备成为语文教师的良好基础', 91, '[{"key":"ethics","label":"职业素养","score":93,"comment":"守时认真，沟通礼貌。"},{"key":"teaching","label":"教学实施","score":90,"comment":"课堂节奏稳定，提问设计较好。"},{"key":"management","label":"班级管理","score":88,"comment":"对课堂秩序把控基本到位。"},{"key":"reflection","label":"反思改进","score":92,"comment":"能根据听评课意见及时调整。"}]', '职业素养良好，备课认真。', '建议继续加强板书设计和分层提问。', '学院同意指导教师建议，建议继续保持课堂反思习惯。', 91, 1, 1, 0, '2026-03-14 10:00:00', NULL, '2026-03-15 09:30:00');

INSERT INTO college_application (id, school_name, college_name, contact_name, contact_phone, description, status, review_comment, created_at) VALUES
('college-app-001', '示范大学', '数学与统计学院', '赵老师', '13500000001', '申请开通教育实习全过程管理平台', '待审核', '', '2026-03-16 08:00:00');

INSERT INTO audit_log (id, type, operator_id, action, detail, created_at) VALUES
('log-001', 'OPERATION', 'user-college-001', '审核实习申请', '通过张三的实习申请', '2026-03-04 08:00:00');

INSERT INTO system_setting (setting_key, setting_value, category, name, description, updated_at) VALUES
('teacher_review_timeout_days', '2', 'REMINDER', '教师审核超时天数', '学生提交表单后，教师超过该天数未处理则产生预警。', '2026-03-01 09:00:00'),
('college_review_timeout_days', '2', 'REMINDER', '学院处理超时天数', '教师审核通过后的学院审批、归档与复核超过该天数则产生预警。', '2026-03-01 09:00:00'),
('student_resubmit_timeout_days', '2', 'REMINDER', '学生退回整改超时天数', '学生被退回后超过该天数未重新提交则产生预警。', '2026-03-01 09:00:00'),
('evaluation_confirm_timeout_days', '3', 'REMINDER', '评价确认超时天数', '教师提交评价后学院超过该天数未确认则产生预警。', '2026-03-01 09:00:00');
