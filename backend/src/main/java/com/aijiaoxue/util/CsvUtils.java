package com.aijiaoxue.util;

import com.aijiaoxue.common.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 工具：负责导出编码与导入解析。
 *
 * <p>导出统一写 UTF-8 BOM，保证 Excel 直接双击打开不乱码；
 * 导入解析支持双引号包裹、字段内逗号与双引号转义，并忽略 BOM 与空行。
 */
public final class CsvUtils {

    /** Excel 友好 BOM */
    private static final String UTF8_BOM = "\uFEFF";

    private CsvUtils() {
    }

    /**
     * 写出 CSV 到响应流。
     *
     * @param response HTTP 响应
     * @param fileName 下载文件名（不含扩展名）
     * @param headers  表头
     * @param rows     数据行
     */
    public static void write(HttpServletResponse response, String fileName,
                             List<String> headers, List<List<String>> rows) throws IOException {
        String encoded = URLEncoder.encode(fileName + ".csv", StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);

        try (PrintWriter writer = response.getWriter()) {
            writer.write(UTF8_BOM);
            writer.write(join(headers));
            writer.write("\r\n");
            if (rows != null) {
                for (List<String> row : rows) {
                    writer.write(join(row));
                    writer.write("\r\n");
                }
            }
            writer.flush();
        }
    }

    /** 拼接一行 CSV，自动转义 */
    private static String join(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        return sb.toString();
    }

    /** 转义：含逗号、双引号、换行时用双引号包裹并转义内部双引号 */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 解析上传的 CSV 文件为二维字符串列表。
     *
     * @throws BusinessException 文件为空或读取失败
     */
    public static List<List<String>> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的 CSV 文件");
        }
        List<List<String>> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    // 去掉可能存在的 BOM
                    if (line.startsWith(UTF8_BOM)) {
                        line = line.substring(1);
                    }
                    first = false;
                }
                if (line.isBlank()) {
                    continue;
                }
                result.add(parseLine(line));
            }
        } catch (IOException e) {
            throw new BusinessException("读取 CSV 文件失败：" + e.getMessage());
        }
        return result;
    }

    /** 解析单行，支持引号包裹字段 */
    private static List<String> parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuote = true;
            } else if (c == ',') {
                cells.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cells.add(cur.toString().trim());
        return cells;
    }

    /** 安全取列，越界返回 null */
    public static String cell(List<String> row, int index) {
        if (row == null || index >= row.size()) {
            return null;
        }
        String v = row.get(index);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
