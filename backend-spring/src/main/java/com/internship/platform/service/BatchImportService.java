package com.internship.platform.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.internship.platform.common.BizException;
import com.internship.platform.constant.RoleType;
import com.internship.platform.dto.Requests;
import com.internship.platform.entity.AuditLogEntity;
import com.internship.platform.entity.StudentEntity;
import com.internship.platform.entity.TeacherEntity;
import com.internship.platform.entity.UserAccountEntity;
import com.internship.platform.mapper.AuditLogMapper;
import com.internship.platform.mapper.StudentMapper;
import com.internship.platform.mapper.TeacherMapper;
import com.internship.platform.mapper.UserAccountMapper;
import com.internship.platform.security.LoginUser;
import com.internship.platform.util.IdGenerator;
import com.internship.platform.util.PasswordUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BatchImportService {

    private static final String DEFAULT_PASSWORD = "123456";
    private static final String STUDENT_INITIAL_STATUS = "\u5f85\u7533\u8bf7";
    private static final String DETAIL_STATUS_SUCCESS = "SUCCESS";
    private static final String DETAIL_STATUS_FAILED = "FAILED";

    private record CsvImportRow(int rowNumber, Map<String, String> values) {
    }

    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final UserAccountMapper userAccountMapper;
    private final AuditLogMapper auditLogMapper;

    public Map<String, Object> importStudents(LoginUser loginUser, MultipartFile file) {
        requireCollegeAdmin(loginUser);
        List<CsvImportRow> rows = parseRows(file, studentHeaderAliases());
        Set<String> seenStudentNos = new HashSet<>();
        int successCount = 0;
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();

        for (CsvImportRow row : rows) {
            String userId = null;
            String studentId = null;
            String name = row.values().getOrDefault("name", "");
            String studentNo = row.values().getOrDefault("studentNo", "");
            try {
                Requests.StudentCreateRequest request = toStudentRequest(row);
                name = request.name().trim();
                studentNo = request.studentNo().trim();
                if (!seenStudentNos.add(studentNo)) {
                    throw new BizException("\u7b2c " + row.rowNumber() + " \u884c\u5b66\u53f7\u91cd\u590d\uff1a" + studentNo);
                }
                if (studentExists(studentNo) || accountExists(studentNo)) {
                    throw new BizException("\u7b2c " + row.rowNumber() + " \u884c\u5b66\u53f7\u5df2\u5b58\u5728\uff1a" + studentNo);
                }

                userId = IdGenerator.nextId("user");
                studentId = IdGenerator.nextId("student");
                createStudentRecord(loginUser, request, userId, studentId);
                successCount += 1;
                details.add(buildDetail(row.rowNumber(), DETAIL_STATUS_SUCCESS, studentNo, name, "\u5bfc\u5165\u6210\u529f"));
            } catch (Exception exception) {
                rollbackStudentInsert(studentId, userId);
                String message = resolveRowError(row.rowNumber(), exception);
                errors.add(message);
                details.add(buildDetail(row.rowNumber(), DETAIL_STATUS_FAILED, studentNo, name, message));
            }
        }

        insertAudit(
                loginUser.id(),
                "\u6279\u91cf\u5bfc\u5165\u5b66\u751f",
                "\u6210\u529f " + successCount + " \u6761\uff0c\u8df3\u8fc7 " + errors.size() + " \u6761"
        );
        return buildResult(rows.size(), successCount, errors, details);
    }

    public byte[] buildStudentTemplate(LoginUser loginUser) {
        requireCollegeAdmin(loginUser);
        return buildTemplateWorkbook(
                "学生导入模板",
                List.of("姓名", "学号", "专业", "班级", "联系电话", "实习类型"),
                List.of(
                        List.of("张三", "20230001", "小学教育", "小教1班", "13800000001", "TEACHING"),
                        List.of("李四", "20230002", "学前教育", "学前2班", "13800000002", "HEAD_TEACHER")
                ),
                List.of(
                        "1. 表头请保持不变，支持按此模板直接导入。",
                        "2. 实习类型仅支持 TEACHING 或 HEAD_TEACHER。",
                        "3. 联系电话建议填写常用手机号，默认初始密码为 123456。"
                )
        );
    }

    public byte[] buildTeacherTemplate(LoginUser loginUser) {
        requireCollegeAdmin(loginUser);
        return buildTemplateWorkbook(
                "教师导入模板",
                List.of("姓名", "工号", "部门", "联系电话"),
                List.of(
                        List.of("王老师", "T2023001", "语文教研室", "13900000001"),
                        List.of("李老师", "T2023002", "数学教研室", "13900000002")
                ),
                List.of(
                        "1. 表头请保持不变，支持按此模板直接导入。",
                        "2. 工号不可重复，默认初始密码为 123456。",
                        "3. 部门建议填写教研室或所属院系名称。"
                )
        );
    }

    public Map<String, Object> importTeachers(LoginUser loginUser, MultipartFile file) {
        requireCollegeAdmin(loginUser);
        List<CsvImportRow> rows = parseRows(file, teacherHeaderAliases());
        Set<String> seenEmployeeNos = new HashSet<>();
        int successCount = 0;
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();

        for (CsvImportRow row : rows) {
            String userId = null;
            String teacherId = null;
            String name = row.values().getOrDefault("name", "");
            String employeeNo = row.values().getOrDefault("employeeNo", "");
            try {
                Requests.TeacherCreateRequest request = toTeacherRequest(row);
                name = request.name().trim();
                employeeNo = request.employeeNo().trim();
                if (!seenEmployeeNos.add(employeeNo)) {
                    throw new BizException("\u7b2c " + row.rowNumber() + " \u884c\u5de5\u53f7\u91cd\u590d\uff1a" + employeeNo);
                }
                if (teacherExists(employeeNo) || accountExists(employeeNo)) {
                    throw new BizException("\u7b2c " + row.rowNumber() + " \u884c\u5de5\u53f7\u5df2\u5b58\u5728\uff1a" + employeeNo);
                }

                userId = IdGenerator.nextId("user");
                teacherId = IdGenerator.nextId("teacher");
                createTeacherRecord(loginUser, request, userId, teacherId);
                successCount += 1;
                details.add(buildDetail(row.rowNumber(), DETAIL_STATUS_SUCCESS, employeeNo, name, "\u5bfc\u5165\u6210\u529f"));
            } catch (Exception exception) {
                rollbackTeacherInsert(teacherId, userId);
                String message = resolveRowError(row.rowNumber(), exception);
                errors.add(message);
                details.add(buildDetail(row.rowNumber(), DETAIL_STATUS_FAILED, employeeNo, name, message));
            }
        }

        insertAudit(
                loginUser.id(),
                "\u6279\u91cf\u5bfc\u5165\u6559\u5e08",
                "\u6210\u529f " + successCount + " \u6761\uff0c\u8df3\u8fc7 " + errors.size() + " \u6761"
        );
        return buildResult(rows.size(), successCount, errors, details);
    }

    private byte[] buildTemplateWorkbook(String sheetName, List<String> headers, List<List<String>> sampleRows, List<String> tips) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle noteStyle = workbook.createCellStyle();
            Font noteFont = workbook.createFont();
            noteFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            noteStyle.setFont(noteFont);
            noteStyle.setWrapText(true);

            Sheet dataSheet = workbook.createSheet(sheetName);
            dataSheet.createFreezePane(0, 1);
            Row headerRow = dataSheet.createRow(0);
            for (int index = 0; index < headers.size(); index += 1) {
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(headers.get(index));
                cell.setCellStyle(headerStyle);
            }

            for (int rowIndex = 0; rowIndex < sampleRows.size(); rowIndex += 1) {
                Row row = dataSheet.createRow(rowIndex + 1);
                List<String> sample = sampleRows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < headers.size(); columnIndex += 1) {
                    row.createCell(columnIndex).setCellValue(columnIndex < sample.size() ? sample.get(columnIndex) : "");
                }
            }

            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex += 1) {
                dataSheet.autoSizeColumn(columnIndex);
                dataSheet.setColumnWidth(columnIndex, Math.min(dataSheet.getColumnWidth(columnIndex) + 1024, 30 * 256));
            }

            Sheet helpSheet = workbook.createSheet("填写说明");
            for (int index = 0; index < tips.size(); index += 1) {
                Row row = helpSheet.createRow(index);
                Cell cell = row.createCell(0);
                cell.setCellValue(tips.get(index));
                cell.setCellStyle(noteStyle);
            }
            helpSheet.setColumnWidth(0, 100 * 256);
            workbook.setActiveSheet(0);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new BizException("生成导入模板失败");
        }
    }

    private Map<String, Object> buildDetail(int rowNumber, String status, String identifier, String name, String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("rowNumber", rowNumber);
        detail.put("status", status);
        detail.put("identifier", Optional.ofNullable(identifier).orElse(""));
        detail.put("name", Optional.ofNullable(name).orElse(""));
        detail.put("message", Optional.ofNullable(message).orElse(""));
        return detail;
    }

    private void requireCollegeAdmin(LoginUser loginUser) {
        if (!RoleType.COLLEGE_ADMIN.name().equals(loginUser.role())) {
            throw new BizException("\u5f53\u524d\u89d2\u8272\u65e0\u6743\u6267\u884c\u6279\u91cf\u5bfc\u5165");
        }
    }

    private Requests.StudentCreateRequest toStudentRequest(CsvImportRow row) {
        String internshipType = normalizeInternshipType(requiredValue(row, "internshipType", "\u5b9e\u4e60\u7c7b\u578b"));
        return new Requests.StudentCreateRequest(
                requiredValue(row, "name", "\u59d3\u540d"),
                requiredValue(row, "studentNo", "\u5b66\u53f7"),
                requiredValue(row, "major", "\u4e13\u4e1a"),
                requiredValue(row, "className", "\u73ed\u7ea7"),
                requiredValue(row, "phone", "\u8054\u7cfb\u7535\u8bdd"),
                internshipType
        );
    }

    private Requests.TeacherCreateRequest toTeacherRequest(CsvImportRow row) {
        return new Requests.TeacherCreateRequest(
                requiredValue(row, "name", "\u59d3\u540d"),
                requiredValue(row, "employeeNo", "\u5de5\u53f7"),
                requiredValue(row, "department", "\u90e8\u95e8"),
                requiredValue(row, "phone", "\u8054\u7cfb\u7535\u8bdd")
        );
    }

    private String requiredValue(CsvImportRow row, String key, String label) {
        String value = row.values().getOrDefault(key, "").trim();
        if (value.isBlank()) {
            throw new BizException("\u7b2c " + row.rowNumber() + " \u884c\u7f3a\u5c11\u5fc5\u586b\u5217\uff1a" + label);
        }
        return value;
    }

    private String normalizeInternshipType(String raw) {
        String value = raw.trim();
        if (value.equalsIgnoreCase("TEACHING") || value.equals("\u4efb\u8bfe\u5b9e\u4e60") || value.equals("\u6559\u5b66\u5b9e\u4e60")) {
            return "TEACHING";
        }
        if (value.equalsIgnoreCase("HEAD_TEACHER") || value.equals("\u73ed\u4e3b\u4efb\u5b9e\u4e60")) {
            return "HEAD_TEACHER";
        }
        throw new BizException("\u5b9e\u4e60\u7c7b\u578b\u4ec5\u652f\u6301 TEACHING\u3001HEAD_TEACHER\u3001\u4efb\u8bfe\u5b9e\u4e60\u3001\u73ed\u4e3b\u4efb\u5b9e\u4e60");
    }

    private void createStudentRecord(LoginUser loginUser, Requests.StudentCreateRequest request, String userId, String studentId) {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(userId);
        user.setAccount(request.studentNo());
        user.setName(request.name());
        user.setRole(RoleType.STUDENT.name());
        user.setPassword(PasswordUtils.sha256(DEFAULT_PASSWORD));
        user.setMustChangePassword(true);
        user.setStatus("ACTIVE");
        user.setCollegeId(loginUser.collegeId());
        userAccountMapper.insert(user);

        StudentEntity student = new StudentEntity();
        student.setId(studentId);
        student.setUserId(userId);
        student.setName(request.name());
        student.setStudentNo(request.studentNo());
        student.setCollegeId(loginUser.collegeId());
        student.setMajor(request.major());
        student.setClassName(request.className());
        student.setPhone(request.phone());
        student.setInternshipType(request.internshipType());
        student.setInternshipStatus(STUDENT_INITIAL_STATUS);
        student.setProfileCompleted(true);
        studentMapper.insert(student);
    }

    private void createTeacherRecord(LoginUser loginUser, Requests.TeacherCreateRequest request, String userId, String teacherId) {
        UserAccountEntity user = new UserAccountEntity();
        user.setId(userId);
        user.setAccount(request.employeeNo());
        user.setName(request.name());
        user.setRole(RoleType.TEACHER.name());
        user.setPassword(PasswordUtils.sha256(DEFAULT_PASSWORD));
        user.setMustChangePassword(true);
        user.setStatus("ACTIVE");
        user.setCollegeId(loginUser.collegeId());
        userAccountMapper.insert(user);

        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(teacherId);
        teacher.setUserId(userId);
        teacher.setName(request.name());
        teacher.setEmployeeNo(request.employeeNo());
        teacher.setCollegeId(loginUser.collegeId());
        teacher.setDepartment(request.department());
        teacher.setPhone(request.phone());
        teacher.setStatus("ACTIVE");
        teacherMapper.insert(teacher);
    }

    private void rollbackStudentInsert(String studentId, String userId) {
        if (studentId != null) {
            studentMapper.deleteById(studentId);
        }
        if (userId != null) {
            userAccountMapper.deleteById(userId);
        }
    }

    private void rollbackTeacherInsert(String teacherId, String userId) {
        if (teacherId != null) {
            teacherMapper.deleteById(teacherId);
        }
        if (userId != null) {
            userAccountMapper.deleteById(userId);
        }
    }

    private boolean studentExists(String studentNo) {
        return studentMapper.selectCount(Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getStudentNo, studentNo)) > 0;
    }

    private boolean teacherExists(String employeeNo) {
        return teacherMapper.selectCount(Wrappers.<TeacherEntity>lambdaQuery().eq(TeacherEntity::getEmployeeNo, employeeNo)) > 0;
    }

    private boolean accountExists(String account) {
        return userAccountMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getAccount, account)) > 0;
    }

    private Map<String, Object> buildResult(int totalRows, int successCount, List<String> errors, List<Map<String, Object>> details) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRows", totalRows);
        result.put("successCount", successCount);
        result.put("skippedCount", errors.size());
        result.put("errors", errors);
        result.put("details", details);
        result.put("message", "\u6210\u529f\u5bfc\u5165 " + successCount + " \u6761");
        return result;
    }

    private void insertAudit(String operatorId, String action, String detail) {
        AuditLogEntity auditLog = new AuditLogEntity();
        auditLog.setId(IdGenerator.nextId("audit"));
        auditLog.setType("IMPORT");
        auditLog.setOperatorId(operatorId);
        auditLog.setAction(action);
        auditLog.setDetail(detail);
        auditLog.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(auditLog);
    }

    private String resolveRowError(int rowNumber, Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "\u7b2c " + rowNumber + " \u884c\u5bfc\u5165\u5931\u8d25";
        }
        if (message.startsWith("\u7b2c ")) {
            return message;
        }
        return "\u7b2c " + rowNumber + " \u884c\uff1a" + message;
    }

    private Map<String, List<String>> studentHeaderAliases() {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("name", List.of("name", "\u59d3\u540d"));
        aliases.put("studentNo", List.of("studentno", "\u5b66\u53f7"));
        aliases.put("major", List.of("major", "\u4e13\u4e1a"));
        aliases.put("className", List.of("classname", "\u73ed\u7ea7"));
        aliases.put("phone", List.of("phone", "\u8054\u7cfb\u7535\u8bdd", "\u624b\u673a\u53f7", "\u624b\u673a", "\u7535\u8bdd"));
        aliases.put("internshipType", List.of("internshiptype", "\u5b9e\u4e60\u7c7b\u578b"));
        return aliases;
    }

    private Map<String, List<String>> teacherHeaderAliases() {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("name", List.of("name", "\u59d3\u540d"));
        aliases.put("employeeNo", List.of("employeeno", "\u5de5\u53f7"));
        aliases.put("department", List.of("department", "\u90e8\u95e8", "\u6559\u7814\u5ba4"));
        aliases.put("phone", List.of("phone", "\u8054\u7cfb\u7535\u8bdd", "\u624b\u673a\u53f7", "\u624b\u673a", "\u7535\u8bdd"));
        return aliases;
    }

    private List<CsvImportRow> parseRows(MultipartFile file, Map<String, List<String>> aliasMap) {
        List<List<String>> records = readRecords(file);
        if (records.isEmpty()) {
            throw new BizException("\u5bfc\u5165\u6587\u4ef6\u4e3a\u7a7a");
        }

        List<String> headers = records.get(0);
        Map<String, Integer> columnIndexes = resolveColumnIndexes(headers, aliasMap);
        List<CsvImportRow> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i += 1) {
            List<String> record = records.get(i);
            if (record.stream().allMatch(item -> item == null || item.trim().isBlank())) {
                continue;
            }
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, Integer> entry : columnIndexes.entrySet()) {
                int index = entry.getValue();
                String value = index < record.size() ? record.get(index) : "";
                values.put(entry.getKey(), value == null ? "" : value.trim());
            }
            rows.add(new CsvImportRow(i + 1, values));
        }
        if (rows.isEmpty()) {
            throw new BizException("\u5bfc\u5165\u6587\u4ef6\u6ca1\u6709\u53ef\u5904\u7406\u7684\u6570\u636e\u884c");
        }
        return rows;
    }

    private List<List<String>> readRecords(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("\u8bf7\u5148\u9009\u62e9\u5bfc\u5165\u6587\u4ef6");
        }

        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            return parseSpreadsheetRecords(file);
        }
        if (fileName.endsWith(".csv") || fileName.isBlank()) {
            return parseCsvRecords(file);
        }
        throw new BizException("\u4ec5\u652f\u6301 CSV\u3001XLSX \u6216 XLS \u5bfc\u5165");
    }

    private List<List<String>> parseCsvRecords(MultipartFile file) {
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BizException("\u8bfb\u53d6\u5bfc\u5165\u6587\u4ef6\u5931\u8d25");
        }
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        return parseCsvContent(content);
    }

    private List<List<String>> parseSpreadsheetRecords(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BizException("Excel \u5de5\u4f5c\u7c3f\u4e3a\u7a7a");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<List<String>> records = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastRowNum; rowIndex += 1) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    records.add(new ArrayList<>());
                    continue;
                }
                int lastCellNum = Math.max(row.getLastCellNum(), 0);
                List<String> cells = new ArrayList<>();
                for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex += 1) {
                    Cell cell = row.getCell(cellIndex);
                    String value = cell == null ? "" : formatter.formatCellValue(cell, evaluator);
                    cells.add(value == null ? "" : value.trim());
                }
                trimTrailingBlanks(cells);
                records.add(cells);
            }
            return records;
        } catch (IOException exception) {
            throw new BizException("\u8bfb\u53d6 Excel \u6587\u4ef6\u5931\u8d25");
        } catch (Exception exception) {
            throw new BizException("Excel \u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u786e\u8ba4\u6587\u4ef6\u5185\u5bb9\u548c\u683c\u5f0f\u6b63\u786e");
        }
    }

    private void trimTrailingBlanks(List<String> cells) {
        for (int index = cells.size() - 1; index >= 0; index -= 1) {
            if (!cells.get(index).isBlank()) {
                return;
            }
            cells.remove(index);
        }
    }

    private Map<String, Integer> resolveColumnIndexes(List<String> headers, Map<String, List<String>> aliasMap) {
        Map<String, Integer> normalizedIndex = new HashMap<>();
        for (int i = 0; i < headers.size(); i += 1) {
            normalizedIndex.put(normalizeHeader(headers.get(i)), i);
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : aliasMap.entrySet()) {
            Integer index = null;
            for (String alias : entry.getValue()) {
                index = normalizedIndex.get(normalizeHeader(alias));
                if (index != null) {
                    break;
                }
            }
            if (index == null) {
                missing.add(entry.getValue().get(entry.getValue().size() - 1));
            } else {
                result.put(entry.getKey(), index);
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException("\u5bfc\u5165\u6a21\u677f\u7f3a\u5c11\u5217\uff1a" + String.join("\u3001", missing));
        }
        return result;
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.replace("\uFEFF", "").replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }

    private List<List<String>> parseCsvContent(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i += 1) {
            char current = content.charAt(i);
            if (inQuotes) {
                if (current == '"') {
                    boolean escapedQuote = i + 1 < content.length() && content.charAt(i + 1) == '"';
                    if (escapedQuote) {
                        currentCell.append('"');
                        i += 1;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentCell.append(current);
                }
                continue;
            }

            if (current == '"') {
                inQuotes = true;
                continue;
            }
            if (current == ',') {
                currentRow.add(currentCell.toString());
                currentCell.setLength(0);
                continue;
            }
            if (current == '\r') {
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i += 1;
                }
                currentRow.add(currentCell.toString());
                currentCell.setLength(0);
                records.add(currentRow);
                currentRow = new ArrayList<>();
                continue;
            }
            if (current == '\n') {
                currentRow.add(currentCell.toString());
                currentCell.setLength(0);
                records.add(currentRow);
                currentRow = new ArrayList<>();
                continue;
            }
            currentCell.append(current);
        }

        currentRow.add(currentCell.toString());
        if (!(currentRow.size() == 1 && currentRow.get(0).trim().isEmpty() && !records.isEmpty())) {
            records.add(currentRow);
        }
        return records;
    }
}