package com.repairmanagement.repairproject.repair;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.inventory.*;
import com.repairmanagement.repairproject.user.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepairService {
    private static final Map<RepairStatus, Set<RepairStatus>> ALLOWED = Map.of(
        RepairStatus.NEW, Set.of(RepairStatus.DIAGNOSING, RepairStatus.CANCELLED),
        RepairStatus.DIAGNOSING, Set.of(RepairStatus.WAITING_PARTS, RepairStatus.IN_PROGRESS, RepairStatus.CANCELLED),
        RepairStatus.WAITING_PARTS, Set.of(RepairStatus.IN_PROGRESS, RepairStatus.CANCELLED),
        RepairStatus.IN_PROGRESS, Set.of(RepairStatus.WAITING_PARTS, RepairStatus.COMPLETED, RepairStatus.CANCELLED),
        RepairStatus.COMPLETED, Set.of(RepairStatus.DELIVERED),
        RepairStatus.DELIVERED, Set.of(),
        RepairStatus.CANCELLED, Set.of()
    );

    private final RepairRequestRepository repairs;
    private final RepairPartRepository repairParts;
    private final RepairUpdateRepository repairUpdates;
    private final ItemRepository items;
    private final StockMovementRepository stockMovements;
    private final CompanyMemberRepository companyMembers;
    private final UserRepository users;
    private final UserService userService;
    private final InventoryAccess access;

    public RepairService(RepairRequestRepository repairs, RepairPartRepository repairParts,
                         RepairUpdateRepository repairUpdates, ItemRepository items,
                         StockMovementRepository stockMovements, CompanyMemberRepository companyMembers,
                         UserRepository users, UserService userService, InventoryAccess access) {
        this.repairs = repairs;
        this.repairParts = repairParts;
        this.repairUpdates = repairUpdates;
        this.items = items;
        this.stockMovements = stockMovements;
        this.companyMembers = companyMembers;
        this.users = users;
        this.userService = userService;
        this.access = access;
    }

    @Transactional
    public RepairView create(Long companyId, CreateRepairRequest body, String email) {
        User customer = userService.currentUser(email);
        requireEligibleClient(customer);
        Company company = access.requireActiveCompany(companyId);
        return saveNewRepair(company, customer, customer, body.clientPhone(),
            body.deviceType(), body.brand(), body.model(), body.issueDescription());
    }

    /** Only active CLIENT accounts without ANY active company membership are selectable. */
    @Transactional(readOnly = true)
    public List<RepairClientOption> searchClients(Long companyId, String search, String email) {
        access.requireMember(companyId, email);
        String term = search == null ? "" : search.trim();
        if (term.length() < 2 || term.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search needs 2–80 characters");
        }
        return users.findEligibleRepairClients(PlatformRole.CLIENT, term, PageRequest.of(0, 20))
            .stream().map(RepairClientOption::from).toList();
    }

    /** Staff can register a repair only for an actual client user ID, not a typed name. */
    @Transactional
    public RepairView createForClient(Long companyId, CreateRepairForClientRequest body, String email) {
        User staff = access.requireMember(companyId, email);
        User customer = users.findById(body.clientId()).filter(User::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        requireEligibleClient(customer);
        Company company = access.requireActiveCompany(companyId);
        return saveNewRepair(company, customer, staff, body.clientPhone(),
            body.deviceType(), body.brand(), body.model(), body.issueDescription());
    }

    private void requireEligibleClient(User customer) {
        if (customer.getPlatformRole() != PlatformRole.CLIENT ||
            !companyMembers.findByUser_IdAndActiveTrue(customer.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only client accounts without active company roles can own repair requests");
        }
    }

    private RepairView saveNewRepair(Company company, User customer, User actor,
                                     String phone, String type, String brand, String model, String issue) {
        RepairRequest repair = repairs.saveAndFlush(new RepairRequest(company, customer,
            customer.getFullName(), phone.trim(), type.trim(), trim(brand), trim(model), issue.trim()));
        repairUpdates.save(new RepairUpdate(repair, actor, RepairStatus.NEW, "Repair request received"));
        return view(repair, false, false);
    }

    @Transactional(readOnly = true)
    public List<RepairView> mine(Long companyId, String email) {
        User customer = userService.currentUser(email);
        access.requireActiveCompany(companyId);
        return repairs.findByCompany_IdAndCustomer_IdOrderByCreatedAtDesc(companyId, customer.getId())
            .stream().map(r -> view(r, false, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<RepairView> companyRepairs(Long companyId, String email) {
        User staff = access.requireMember(companyId, email);
        boolean costs = isManager(companyId, staff.getId());
        return repairs.findByCompany_IdOrderByCreatedAtDesc(companyId)
            .stream().map(r -> view(r, costs, true)).toList();
    }

    @Transactional(readOnly = true)
    public RepairView get(Long companyId, Long repairId, String email) {
        User viewer = userService.currentUser(email);
        access.requireActiveCompany(companyId);
        RepairRequest repair = find(companyId, repairId);
        if (repair.getCustomer().getId().equals(viewer.getId())) {
            return view(repair, false, false);
        }
        access.requireMember(companyId, email);
        return view(repair, isManager(companyId, viewer.getId()), true);
    }

    @Transactional(readOnly = true)
    public RepairView getForStaff(Long companyId, Long repairId, String email) {
        User viewer = access.requireMember(companyId, email);
        return view(find(companyId, repairId), isManager(companyId, viewer.getId()), true);
    }

    @Transactional
    public RepairView assign(Long companyId, Long repairId, AssignRepairRequest body, String email) {
        User manager = access.requireManager(companyId, email);
        RepairRequest repair = locked(companyId, repairId);
        requireOpen(repair);
        User employee = users.findById(body.employeeId()).filter(User::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        companyMembers.findByCompany_IdAndUser_IdAndActiveTrue(companyId, employee.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Assigned employee must be an active member of this company"));
        repair.assign(employee);
        repairUpdates.save(new RepairUpdate(repair, manager, repair.getStatus(),
            "Assigned to staff member #" + employee.getId()));
        return view(repair, true, true);
    }

    @Transactional
    public RepairView changeStatus(Long companyId, Long repairId, ChangeRepairStatusRequest body, String email) {
        User actor = access.requireMember(companyId, email);
        RepairRequest repair = locked(companyId, repairId);
        requireAssignedOrManager(companyId, actor, repair);
        RepairStatus next = body.status();
        if (!ALLOWED.get(repair.getStatus()).contains(next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid repair status transition");
        }
        if (next == RepairStatus.DELIVERED && !isManager(companyId, actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a manager can mark a repair delivered");
        }
        if (next == RepairStatus.COMPLETED &&
            (repair.getAssignedEmployee() == null ||
             repair.getDiagnosis() == null || repair.getDiagnosis().isBlank() ||
             repair.getWorkPerformed() == null || repair.getWorkPerformed().isBlank())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Assign a technician and save the diagnosis/work report before completing the repair");
        }
        // Physical parts already consumed must be handled explicitly rather than returned blindly.
        if (next == RepairStatus.CANCELLED && repairParts.existsByRepair_Id(repair.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot cancel after parts have been consumed; use an approved return workflow");
        }
        repair.changeStatus(next);
        repairUpdates.save(new RepairUpdate(repair, actor, next,
            trim(body.note()), trim(body.customerNote())));
        return view(repair, isManager(companyId, actor.getId()), true);
    }

    @Transactional
    public RepairView addPart(Long companyId, Long repairId, AddRepairPartRequest body, String email) {
        User actor = access.requireMember(companyId, email);
        RepairRequest repair = locked(companyId, repairId);
        requireAssignedOrManager(companyId, actor, repair);
        if (repair.getStatus() != RepairStatus.DIAGNOSING &&
            repair.getStatus() != RepairStatus.WAITING_PARTS &&
            repair.getStatus() != RepairStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Parts can be used only during diagnosis or active repair");
        }
        if (body.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }
        Item item = items.lockForUpdate(companyId, body.itemId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        if (item.compatibilityUnspecified() && !body.confirmUnverifiedCompatibility()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Compatibility has not been verified. Confirm fit before using this part");
        }
        if (!item.compatibilityUnspecified() &&
            !item.supportsDevice(repair.getDeviceType(), repair.getBrand(), repair.getModel())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Part is not listed as compatible with this repair's device type, brand and model");
        }
        if (!item.isActive() || item.getStockQuantity() < body.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Part unavailable or insufficient stock");
        }
        RepairPart part = new RepairPart(repair, item, body.quantity());
        item.setStockQuantity(item.getStockQuantity() - body.quantity());
        repairParts.saveAndFlush(part);
        stockMovements.save(new StockMovement(item, actor, MovementType.REPAIR, -body.quantity(),
            "Repair #" + repair.getId()));
        repairUpdates.save(new RepairUpdate(repair, actor, repair.getStatus(),
            "Replacement part added: " + item.getName() + " x" + body.quantity()));
        return view(repair, isManager(companyId, actor.getId()), true);
    }

    /** Return an unused/mistakenly consumed part to inventory before repair completion. */
    @Transactional
    public RepairView returnPart(Long companyId, Long repairId, Long partId, String email) {
        User actor = access.requireMember(companyId, email);
        RepairRequest repair = locked(companyId, repairId);
        requireAssignedOrManager(companyId, actor, repair);
        if (repair.getStatus() != RepairStatus.DIAGNOSING &&
            repair.getStatus() != RepairStatus.WAITING_PARTS &&
            repair.getStatus() != RepairStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Parts can only be returned during an active repair");
        }
        RepairPart part = repairParts.findById(partId)
            .filter(p -> p.getRepair().getId().equals(repair.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repair part not found"));
        Item item = items.lockForUpdate(companyId, part.getItem().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));
        long next = (long)item.getStockQuantity() + part.getQuantity();
        if (next > Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock quantity overflow");
        item.setStockQuantity((int)next);
        stockMovements.save(new StockMovement(item, actor, MovementType.RETURN, part.getQuantity(),
            "Part returned from repair #" + repair.getId() + ", repair part #" + partId));
        repairParts.delete(part);
        repairParts.flush();
        repairUpdates.save(new RepairUpdate(repair, actor, repair.getStatus(),
            "Replacement part returned to stock: " + item.getName() + " x" + part.getQuantity()));
        return view(repair, isManager(companyId, actor.getId()), true);
    }

    @Transactional
    public RepairView updateLabor(Long companyId, Long repairId, UpdateLaborRequest body, String email) {
        User actor = access.requireManager(companyId, email);
        RepairRequest repair = locked(companyId, repairId);
        requireOpen(repair);
        if (body.laborCharge().signum() < 0 || body.laborCost().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Negative amounts are not allowed");
        }
        repair.setLabor(body.laborCharge(), body.laborCost());
        // Do not expose internal labor cost in the customer-visible history notes.
        repairUpdates.save(new RepairUpdate(repair, actor, repair.getStatus(), "Repair charges updated"));
        return view(repair, true, true);
    }

    /** A structured explanation visible on the eventual customer invoice. */
    @Transactional
    public RepairView updateRepairReport(Long companyId, Long repairId,
                                         UpdateRepairReportRequest body, String email) {
        User actor = access.requireMember(companyId, email);
        RepairRequest repair = locked(companyId, repairId);
        requireAssignedOrManager(companyId, actor, repair);
        if (repair.getStatus() == RepairStatus.DELIVERED || repair.getStatus() == RepairStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report is closed");
        }
        if (repair.getStatus() == RepairStatus.COMPLETED && !isManager(companyId, actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only managers can amend a completed repair report");
        }
        repair.recordReport(body.diagnosis(), body.workPerformed());
        repairUpdates.save(new RepairUpdate(repair, actor, repair.getStatus(),
            "Repair report updated; customer invoice will contain diagnosis and work performed"));
        return view(repair, isManager(companyId, actor.getId()), true);
    }

    @Transactional(readOnly = true)
    public RepairProfitSummaryView profitSummary(Long companyId, String email) {
        access.requireManager(companyId, email);
        Company company = access.requireActiveCompany(companyId);
        List<RepairRequest> finalized = repairs.findByCompany_IdAndStatusIn(companyId,
            List.of(RepairStatus.COMPLETED, RepairStatus.DELIVERED));
        BigDecimal laborRevenue = BigDecimal.ZERO, partsRevenue = BigDecimal.ZERO;
        BigDecimal laborCost = BigDecimal.ZERO, partsCost = BigDecimal.ZERO;
        // Sum one time per repair: delivered is a later state of completed, not a second repair.
        for (RepairRequest repair : finalized) {
            laborRevenue = laborRevenue.add(repair.getLaborCharge());
            laborCost = laborCost.add(repair.getLaborCost());
            for (RepairPart part : repairParts.findByRepair_IdOrderByIdAsc(repair.getId())) {
                BigDecimal quantity = BigDecimal.valueOf(part.getQuantity());
                partsRevenue = partsRevenue.add(part.getUnitSellingPrice().multiply(quantity));
                partsCost = partsCost.add(part.getUnitCostPrice().multiply(quantity));
            }
        }
        BigDecimal gross = laborRevenue.add(partsRevenue).subtract(laborCost).subtract(partsCost);
        return new RepairProfitSummaryView(companyId, company.getCurrencyCode(), finalized.size(),
            laborRevenue, partsRevenue, laborCost, partsCost, gross);
    }

    /** A customer may view all of their own repairs, even across multiple companies. */
    @Transactional(readOnly = true)
    public List<CustomerRepairTrackingView> trackingMine(String email) {
        User customer = userService.currentUser(email);
        return repairs.findByCustomer_IdOrderByCreatedAtDesc(customer.getId())
            .stream().map(this::trackingView).toList();
    }

    @Transactional(readOnly = true)
    public CustomerRepairTrackingView trackingOne(Long repairId, String email) {
        User customer = userService.currentUser(email);
        RepairRequest repair = repairs.findByIdAndCustomer_Id(repairId, customer.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repair not found"));
        return trackingView(repair);
    }

    /** Publish progress without forcing a change of status. */
    @Transactional
    public RepairView publishProgress(Long companyId, Long repairId,
                                      PublishRepairProgressRequest body, String email) {
        User actor = access.requireMember(companyId, email);
        RepairRequest repair = locked(companyId, repairId);
        requireAssignedOrManager(companyId, actor, repair);
        requireOpen(repair);
        repairUpdates.save(new RepairUpdate(repair, actor, repair.getStatus(), null,
            body.customerNote().trim()));
        return view(repair, isManager(companyId, actor.getId()), true);
    }

    private CustomerRepairTrackingView trackingView(RepairRequest repair) {
        // Include meaningful status milestones and customer-facing messages, not
        // internal events such as employee assignments or stock adjustments.
        RepairStatus prior = null;
        List<CustomerRepairEventView> milestones = new java.util.ArrayList<>();
        for (RepairUpdate update : repairUpdates.findByRepair_IdOrderByCreatedAtAscIdAsc(repair.getId())) {
            boolean statusChanged = prior == null || update.getNewStatus() != prior;
            if (statusChanged || (update.getCustomerNote() != null && !update.getCustomerNote().isBlank())) {
                milestones.add(CustomerRepairEventView.from(update));
            }
            prior = update.getNewStatus();
        }
        BigDecimal charge = repair.getLaborCharge();
        for (RepairPart part : repairParts.findByRepair_IdOrderByIdAsc(repair.getId())) {
            charge = charge.add(part.getUnitSellingPrice().multiply(BigDecimal.valueOf(part.getQuantity())));
        }
        return new CustomerRepairTrackingView(repair.getId(), repair.getCompany().getId(),
            repair.getCompany().getName(), repair.getDeviceType(), repair.getBrand(),
            repair.getModel(), repair.getIssueDescription(), repair.getStatus(),
            repair.getCompany().getCurrencyCode(), charge, repair.getCreatedAt(),
            repair.getCompletedAt(), milestones);
    }

    private RepairRequest find(Long companyId, Long repairId) {
        return repairs.findByIdAndCompany_Id(repairId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repair not found"));
    }
    private RepairRequest locked(Long companyId, Long repairId) {
        return repairs.lockForUpdate(companyId, repairId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repair not found"));
    }
    private boolean isManager(Long companyId, Long userId) {
        return companyMembers.findByCompany_IdAndUser_IdAndActiveTrue(companyId, userId)
            .map(m -> m.getMemberRole() == MemberRole.OWNER || m.getMemberRole() == MemberRole.MANAGER)
            .orElse(false);
    }
    private void requireAssignedOrManager(Long companyId, User actor, RepairRequest repair) {
        if (!isManager(companyId, actor.getId()) &&
            (repair.getAssignedEmployee() == null ||
             !repair.getAssignedEmployee().getId().equals(actor.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only the assigned employee or a manager may modify this repair");
        }
    }
    private void requireOpen(RepairRequest repair) {
        if (repair.getStatus() == RepairStatus.COMPLETED ||
            repair.getStatus() == RepairStatus.DELIVERED ||
            repair.getStatus() == RepairStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Repair is already closed");
        }
    }
    private RepairView view(RepairRequest repair, boolean costsVisible, boolean staffView) {
        List<RepairPart> parts = repairParts.findByRepair_IdOrderByIdAsc(repair.getId());
        List<RepairUpdateView> timeline = repairUpdates.findByRepair_IdOrderByCreatedAtAscIdAsc(repair.getId())
            .stream().map(u -> RepairUpdateView.of(u, staffView)).toList();
        BigDecimal partsCharge = BigDecimal.ZERO;
        BigDecimal partCosts = BigDecimal.ZERO;
        for (RepairPart part : parts) {
            BigDecimal quantity = BigDecimal.valueOf(part.getQuantity());
            partsCharge = partsCharge.add(part.getUnitSellingPrice().multiply(quantity));
            partCosts = partCosts.add(part.getUnitCostPrice().multiply(quantity));
        }
        BigDecimal totalCharge = repair.getLaborCharge().add(partsCharge);
        BigDecimal estimatedGrossProfit = totalCharge.subtract(repair.getLaborCost()).subtract(partCosts);
        return new RepairView(
            repair.getId(), repair.getCompany().getId(), repair.getCompany().getCurrencyCode(),
            repair.getCustomer().getId(), repair.getClientName(), repair.getClientPhone(),
            !staffView || repair.getAssignedEmployee() == null ? null : repair.getAssignedEmployee().getId(),
            repair.getDeviceType(), repair.getBrand(), repair.getModel(), repair.getIssueDescription(),
            repair.getDiagnosis(), repair.getWorkPerformed(), repair.getStatus(), repair.getLaborCharge(), partsCharge, totalCharge,
            costsVisible ? repair.getLaborCost() : null,
            costsVisible ? partCosts : null,
            costsVisible ? estimatedGrossProfit : null,
            repair.getCreatedAt(), repair.getCompletedAt(),
            parts.stream().map(p -> RepairPartView.of(p, costsVisible)).toList(), timeline
        );
    }
    private static String trim(String value) { return value == null ? null : value.trim(); }
}
