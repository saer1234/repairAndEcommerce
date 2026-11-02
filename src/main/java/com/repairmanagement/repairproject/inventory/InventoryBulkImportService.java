package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.user.User;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * CSV/XLSX inventory import. Preview performs all validation without writing.
 * Import is all-or-nothing, including opening stock movements.
 */
@Service
public class InventoryBulkImportService {
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ROWS = 500;
    private static final int MAX_ERRORS = 50;
    private final InventoryAccess access;
    private final ItemRepository itemRepository;
    private final ItemService itemService;
    private final Validator validator;

    public InventoryBulkImportService(InventoryAccess access, ItemRepository itemRepository,
                                      ItemService itemService, Validator validator) {
        this.access = access;
        this.itemRepository = itemRepository;
        this.itemService = itemService;
        this.validator = validator;
    }

    public record ImportProblem(int row, String message) {}
    public record ImportPreview(int totalRows, int validRows, int skippedExisting, List<ImportProblem> errors,
                                List<String> sampleSkus) {}
    public record ImportResult(int created, String message) {}
    private record ParsedRow(int row, Map<String, String> values) {}
    private record ImportBatch(List<CreateItemRequest> requests, ImportPreview preview) {}

    @Transactional(readOnly = true)
    public ImportPreview preview(Long companyId, String actorEmail, MultipartFile file) {
        access.requireManager(companyId, actorEmail);
        return parseAndValidate(companyId, file).preview();
    }

    @Transactional
    public ImportResult importFile(Long companyId, String actorEmail, MultipartFile file) {
        // Verify permission before reading file; ItemService.create also verifies each creation.
        access.requireManager(companyId, actorEmail);
        ImportBatch batch = parseAndValidate(companyId, file);
        if (!batch.preview().errors().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Import contains invalid rows. Run preview and correct all errors first.");
        }
        for (CreateItemRequest request : batch.requests()) {
            itemService.create(companyId, request, actorEmail);
        }
        return new ImportResult(batch.requests().size(), "Items and opening stock imported successfully");
    }

    private ImportBatch parseAndValidate(Long companyId, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a CSV or XLSX file up to 2 MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<ParsedRow> rows;
        try {
            if (name.endsWith(".csv")) rows = readCsv(file.getBytes());
            else if (name.endsWith(".xlsx")) rows = readXlsx(file.getBytes());
            else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .csv and .xlsx files are supported");
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read inventory file: " + e.getMessage());
        } catch (RuntimeException e) {
            if (e instanceof ResponseStatusException) throw e;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid CSV or XLSX inventory file");
        }
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File has no inventory rows");
        if (rows.size() > MAX_ROWS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 500 rows per import");

        List<ImportProblem> errors = new ArrayList<>();
        List<CreateItemRequest> requests = new ArrayList<>();
        List<String> sample = new ArrayList<>();
        Set<String> fileSkus = new HashSet<>();
        Set<String> existingSkus = new HashSet<>();
        int skippedExisting = 0;
        // Read current company SKUs, including inactive items; these cannot be duplicated.
        for (Item item : itemRepository.findByCompany_IdOrderByNameAsc(companyId)) {
            existingSkus.add(item.getSku().toUpperCase(Locale.ROOT));
        }
        for (ParsedRow row : rows) {
            if (errors.size() >= MAX_ERRORS) break;
            try {
                CreateItemRequest request = toRequest(row.values());
                Set<ConstraintViolation<CreateItemRequest>> violations = validator.validate(request);
                if (!violations.isEmpty()) {
                    String message = violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .sorted().findFirst().orElse("Invalid item fields");
                    throw new IllegalArgumentException(message);
                }
                if (request.openingStock() < 0) throw new IllegalArgumentException("Opening stock cannot be negative");
                if (request.description() != null && request.description().length() > 2000)
                    throw new IllegalArgumentException("Description too long (max 2000)");
                String sku = request.sku().trim().toUpperCase(Locale.ROOT);
                if (!fileSkus.add(sku)) throw new IllegalArgumentException("SKU " + sku + " appears more than once in file");
                if (existingSkus.contains(sku)) { skippedExisting++; continue; }
                if (sample.size() < 8) sample.add(sku);
                requests.add(request);
            } catch (IllegalArgumentException ex) {
                errors.add(new ImportProblem(row.row(), ex.getMessage()));
            }
        }
        return new ImportBatch(requests, new ImportPreview(rows.size(), requests.size(), skippedExisting, errors, sample));
    }

    private static CreateItemRequest toRequest(Map<String, String> raw) {
        Map<String, String> v = new HashMap<>();
        raw.forEach((k,value) -> v.put(normalizeKey(k), value == null ? "" : value.trim()));
        String sku = get(v, "sku").toUpperCase(Locale.ROOT);
        String name = get(v, "name", "productname");
        String desc = get(v, "description", "notes");
        String type = get(v, "devicetype");
        String brand = get(v, "devicebrand", "brand");
        String category = get(v, "category", "componentcategory");
        if (category.isBlank()) category = "General";
        DeviceClassification.requireDeviceType(type, false);
        if (sku.isBlank() || name.isBlank() || type.isBlank() || brand.isBlank())
            throw new IllegalArgumentException("SKU, name, device type and brand are required");
        BigDecimal cost = money(get(v, "costprice", "cost"), "cost price");
        BigDecimal price = money(get(v, "sellingprice", "selling"), "selling price");
        int stock;
        try { stock = Integer.parseInt(get(v, "openingstock", "stockquantity")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Opening stock must be a whole number"); }
        String fitText = get(v, "compatibledevices", "compatiblemodels");
        List<DeviceCompatibilityInput> fits = new ArrayList<>();
        if (!fitText.isBlank()) {
            for (String part : fitText.split(";")) {
                String model = part.trim();
                if (model.isEmpty()) continue;
                String fitType = type, fitBrand = brand;
                if (model.contains("|")) {
                    String[] segments = model.split("\\|", -1);
                    if (segments.length != 3 || segments[0].isBlank() || segments[1].isBlank() || segments[2].isBlank())
                        throw new IllegalArgumentException("Compatible devices format: DeviceType|Brand|Model;...");
                    fitType = segments[0].trim(); fitBrand = segments[1].trim(); model = segments[2].trim();
                } else if (model.regionMatches(true, 0, brand + " ", 0, brand.length() + 1)) {
                    model = model.substring(brand.length() + 1).trim();
                } else if (model.matches("(?i)^(Apple|Samsung|Xiaomi|Huawei|Google|OnePlus)\\s+.+")) {
                    // Spreadsheet has a verified-looking brand prefix: preserve the alternate brand.
                    int idx = model.indexOf(' ');
                    fitBrand = model.substring(0, idx);
                    model = model.substring(idx + 1);
                }
                // Avoid marking generic marketing claims as certified model compatibility.
                if (model.matches("(?i).*(multiple|most|many|universal|various|devices).*")) continue;
                DeviceClassification.requireDeviceType(fitType, true);
                fits.add(new DeviceCompatibilityInput(fitType, fitBrand, model));
            }
        }
        if (fits.size() > 50) throw new IllegalArgumentException("Maximum 50 compatible devices per item");
        return new CreateItemRequest(sku, name, desc, cost, price, stock, category, type, brand, fits);
    }

    private static BigDecimal money(String input, String name) {
        try {
            String normalized = input.replace("€", "").replace("$", "")
                .replace("£", "").replace(" ", "").replace(",", "");
            return new BigDecimal(normalized).setScale(2, java.math.RoundingMode.UNNECESSARY);
        }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid " + name + ": use a number with max 2 decimals"); }
    }

    private static String normalizeKey(String input) {
        return input.replace("\uFEFF", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    private static String get(Map<String, String> row, String... aliases) {
        for (String alias : aliases) {
            String value = row.get(alias);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
    private static List<ParsedRow> readCsv(byte[] data) throws IOException {
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        try (CSVParser csv = CSVParser.parse(text,
                CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).setTrim(true).get())) {
            List<CSVRecord> records = csv.getRecords();
            if (records.isEmpty()) return List.of();
            CSVRecord header = records.getFirst();
            checkHeader(header.size(), java.util.stream.IntStream.range(0, header.size()).mapToObj(header::get).toList());
            List<ParsedRow> out = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                if (record.size() != header.size()) throw new IllegalArgumentException("Row " + record.getRecordNumber() + ": wrong column count");
                Map<String,String> values = new HashMap<>();
                for (int col = 0; col < header.size(); col++) values.put(header.get(col), record.get(col));
                if (values.values().stream().allMatch(String::isBlank)) continue;
                out.add(new ParsedRow((int) record.getRecordNumber(), values));
                if (out.size() > MAX_ROWS) break;
            }
            return out;
        }
    }
    private static List<ParsedRow> readXlsx(byte[] data) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return List.of();
            int cols = headerRow.getLastCellNum();
            DataFormatter formatter = new DataFormatter(Locale.US);
            List<String> header = new ArrayList<>();
            for (int c = 0; c < cols; c++) header.add(formatter.formatCellValue(headerRow.getCell(c)));
            checkHeader(cols, header);
            List<ParsedRow> out = new ArrayList<>();
            for (int r = sheet.getFirstRowNum()+1; r <= sheet.getLastRowNum(); r++) {
                Row line = sheet.getRow(r);
                if (line == null) continue;
                Map<String,String> values = new HashMap<>();
                for (int c=0;c<cols;c++) {
                    Cell cell = line.getCell(c);
                    if (cell != null && cell.getCellType() == CellType.FORMULA &&
                        !List.of("profitunit", "grossmargin", "stockcost", "potentialsales", "potentialprofit").contains(normalizeKey(header.get(c))))
                        throw new IllegalArgumentException("Row " + (r+1) + ": formulas not allowed in import");
                    String key = normalizeKey(header.get(c));
                    if (cell != null && cell.getCellType() == CellType.NUMERIC &&
                         List.of("cost", "costprice", "selling", "sellingprice", "openingstock", "stockquantity").contains(key)) {
                        values.put(header.get(c), BigDecimal.valueOf(cell.getNumericCellValue())
                                .stripTrailingZeros().toPlainString());
                    } else {
                        values.put(header.get(c), cell == null ? "" : formatter.formatCellValue(cell));
                    }
                }
                if (values.values().stream().allMatch(String::isBlank)) continue;
                out.add(new ParsedRow(r+1,values));
                if (out.size() > MAX_ROWS) break;
            }
            return out;
        }
    }
    private static void checkHeader(int size, List<String> header) {
        if (size < 5 || size > 25) throw new IllegalArgumentException("Invalid inventory columns");
        Set<String> names = new HashSet<>();
        for (String value : header) {
            if (!names.add(normalizeKey(value))) throw new IllegalArgumentException("Duplicate column name: " + value);
        }
        if (!names.contains("sku") || (!names.contains("name") && !names.contains("productname")))
            throw new IllegalArgumentException("Missing SKU or product name header");
        if (!names.contains("devicetype") || !(names.contains("devicebrand") || names.contains("brand")))
            throw new IllegalArgumentException("Missing device type or brand header");
    }
}
