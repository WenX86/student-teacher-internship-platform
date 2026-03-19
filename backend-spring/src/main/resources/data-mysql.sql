INSERT INTO college (id, school_name, name, contact_name, contact_phone, description) VALUES
('college-001', '示范大学', '教育学院', '王主任', '13800000001', '一期演示学院');

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

INSERT INTO form_template (code, name, category, applicable_types_json) VALUES
('internship-journal', '实习记录', 'COMMON', '["TEACHING","HEAD_TEACHER"]'),
('internship-reflection', '实习心得', 'COMMON', '["TEACHING","HEAD_TEACHER"]'),
('lecture-record', '听课记录', 'TEACHING', '["TEACHING"]'),
('teaching-clock-in', '教学实习打卡', 'TEACHING', '["TEACHING"]'),
('class-duty-record', '值守记录', 'HEAD_TEACHER', '["HEAD_TEACHER"]'),
('class-meeting-plan', '班会方案', 'HEAD_TEACHER', '["HEAD_TEACHER"]');

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

INSERT INTO evaluation_record (id, teacher_id, student_id, stage_comment, summary_comment, final_score, submitted_to_college, confirmed_by_college) VALUES
('eval-001', 'teacher-001', 'student-001', '课堂组织较为稳定', '具备成为语文教师的良好基础', 91, 1, 1);

INSERT INTO college_application (id, school_name, college_name, contact_name, contact_phone, description, status, review_comment, created_at) VALUES
('college-app-001', '滨江师范学院', '数学与统计学院', '赵老师', '13500000001', '申请开通教育实习全过程管理平台', '待审核', '', '2026-03-16 08:00:00');

INSERT INTO audit_log (id, type, operator_id, action, detail, created_at) VALUES
('log-001', 'OPERATION', 'user-college-001', '审核实习申请', '通过张三的实习申请', '2026-03-04 08:00:00');
