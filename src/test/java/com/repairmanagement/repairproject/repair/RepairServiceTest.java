package com.repairmanagement.repairproject.repair;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.inventory.*;
import com.repairmanagement.repairproject.user.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RepairServiceTest {
    private RepairRequestRepository repairs;
    private RepairPartRepository parts;
    private RepairUpdateRepository updates;
    private ItemRepository items;
    private StockMovementRepository movements;
    private CompanyMemberRepository members;
    private UserRepository users;
    private UserService userService;
    private InventoryAccess access;
    private RepairService service;
    private Company company;
    private User customer;
    private User manager;
    private User employee;
    private RepairRequest repair;
    private Item item;

    @BeforeEach
    void init() {
        repairs = mock(RepairRequestRepository.class);
        parts = mock(RepairPartRepository.class);
        updates = mock(RepairUpdateRepository.class);
        items = mock(ItemRepository.class);
        movements = mock(StockMovementRepository.class);
        members = mock(CompanyMemberRepository.class);
        users = mock(UserRepository.class);
        userService = mock(UserService.class);
        access = mock(InventoryAccess.class);
        service = new RepairService(repairs, parts, updates, items, movements, members,
            users, userService, access);
        company = new Company("Company One", null, null, null, "EUR");
        ReflectionTestUtils.setField(company, "id", 1L);
        customer = new User("Customer", "customer@example.com", "hash", null, PlatformRole.CLIENT);
        manager = new User("Manager", "manager@example.com", "hash", null);
        employee = new User("Employee", "employee@example.com", "hash", null);
        ReflectionTestUtils.setField(customer, "id", 10L);
        ReflectionTestUtils.setField(manager, "id", 11L);
        ReflectionTestUtils.setField(employee, "id", 12L);
        repair = new RepairRequest(company, customer, "Phone", "Samsung", "S24", "Broken screen");
        ReflectionTestUtils.setField(repair, "id", 50L);
        item = new Item(company, "LCD-1", "Screen", null,
            new BigDecimal("50.00"), new BigDecimal("80.00"), 4);
        ReflectionTestUtils.setField(item, "id", 8L);
        when(access.requireActiveCompany(1L)).thenReturn(company);
        when(userService.currentUser("customer@example.com")).thenReturn(customer);
        when(userService.currentUser("manager@example.com")).thenReturn(manager);
        when(access.requireManager(1L, "manager@example.com")).thenReturn(manager);
        when(access.requireMember(1L, "manager@example.com")).thenReturn(manager);
        when(access.requireMember(1L, "employee@example.com")).thenReturn(employee);
        when(members.findByCompany_IdAndUser_IdAndActiveTrue(1L, 11L))
            .thenReturn(Optional.of(new CompanyMember(company, manager, MemberRole.MANAGER)));
        when(members.findByCompany_IdAndUser_IdAndActiveTrue(1L, 12L))
            .thenReturn(Optional.of(new CompanyMember(company, employee, MemberRole.EMPLOYEE)));
        when(repairs.lockForUpdate(1L, 50L)).thenReturn(Optional.of(repair));
    }

    @Test
    void createNewRepairForCustomer() {
        when(repairs.saveAndFlush(any(RepairRequest.class))).thenAnswer(inv -> {
            RepairRequest newRepair = inv.getArgument(0);
            ReflectionTestUtils.setField(newRepair, "id", 99L);
            return newRepair;
        });
        RepairView created = service.create(1L,
            new CreateRepairRequest("+96170123456", "Phone", "Apple", "iPhone", "Battery drains"),
            "customer@example.com");
        assertEquals(RepairStatus.NEW, created.status());
        assertEquals(10L, created.customerId());
        assertEquals("Customer", created.clientName()); // Stored from authenticated account, not request JSON.
        assertEquals("+96170123456", created.clientPhone());
        assertNull(created.laborCost());
        verify(updates).save(argThat(u -> u.getNewStatus() == RepairStatus.NEW));
    }

    @Test
    void employeeCannotSubmitOwnClientRepair() {
        when(userService.currentUser("employee@example.com")).thenReturn(employee);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(1L,
                new CreateRepairRequest("+96170123456", "Phone", "Apple", "iPhone", "Broken"),
                "employee@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(repairs, never()).saveAndFlush(any());
    }

    @Test
    void clientWithActiveCompanyMembershipCannotSubmitOwnRepair() {
        when(members.findByUser_IdAndActiveTrue(10L)).thenReturn(
            List.of(new CompanyMember(company, customer, MemberRole.EMPLOYEE)));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(1L,
                new CreateRepairRequest("+96170123456", "Phone", "Apple", "iPhone", "Broken"),
                "customer@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void staffCanCreateForSelectedRoleFreeClient() {
        when(users.findById(10L)).thenReturn(Optional.of(customer));
        when(repairs.saveAndFlush(any(RepairRequest.class))).thenAnswer(inv -> {
            RepairRequest entity = inv.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 101L);
            return entity;
        });
        RepairView saved = service.createForClient(1L,
            new CreateRepairForClientRequest(10L, "+96170123456", "Phone", "Samsung", "S24", "Broken"),
            "employee@example.com");
        assertEquals(10L, saved.customerId());
        assertEquals("Customer", saved.clientName());
        verify(updates).save(argThat(update -> update.getUpdatedBy() == employee));
    }

    @Test
    void managerCanCreateForSelectedRegisteredClient() {
        when(users.findById(10L)).thenReturn(Optional.of(customer));
        when(repairs.saveAndFlush(any(RepairRequest.class))).thenAnswer(inv -> {
            RepairRequest entity = inv.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 102L);
            return entity;
        });
        RepairView saved = service.createForClient(1L,
            new CreateRepairForClientRequest(10L, "+96170123456", "Phone", "Samsung", "S24", "Broken"),
            "manager@example.com");
        assertEquals(10L, saved.customerId());
        assertEquals("Customer", saved.clientName());
        verify(updates).save(argThat(update -> update.getUpdatedBy() == manager));
    }

    @Test
    void staffCannotSelectOwnerOrMemberAsClient() {
        when(users.findById(11L)).thenReturn(Optional.of(manager));
        ResponseStatusException ownerError = assertThrows(ResponseStatusException.class,
            () -> service.createForClient(1L,
                new CreateRepairForClientRequest(11L, "123456", "Phone", "Samsung", "S24", "Broken"),
                "employee@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, ownerError.getStatusCode());
        when(users.findById(10L)).thenReturn(Optional.of(customer));
        when(members.findByUser_IdAndActiveTrue(10L)).thenReturn(
            List.of(new CompanyMember(company, customer, MemberRole.EMPLOYEE)));
        ResponseStatusException memberError = assertThrows(ResponseStatusException.class,
            () -> service.createForClient(1L,
                new CreateRepairForClientRequest(10L, "123456", "Phone", "Samsung", "S24", "Broken"),
                "employee@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, memberError.getStatusCode());
    }

    @Test
    void clientLookupOnlyRequestsClientRoleAndLimitsResults() {
        when(users.findEligibleRepairClients(eq(PlatformRole.CLIENT), eq("cus"), any()))
            .thenReturn(List.of(customer));
        List<RepairClientOption> options = service.searchClients(1L, "cus", "employee@example.com");
        assertEquals(1, options.size());
        assertEquals(10L, options.get(0).id());
        assertEquals("Customer", options.get(0).fullName());
        assertEquals("customer@example.com", options.get(0).email());
        verify(users).findEligibleRepairClients(eq(PlatformRole.CLIENT), eq("cus"),
            argThat(page -> page.getPageSize() == 20 && page.getPageNumber() == 0));
    }

    @Test
    void clientLookupRequiresCompanyMembership() {
        when(access.requireMember(1L, "customer@example.com"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThrows(ResponseStatusException.class,
            () -> service.searchClients(1L, "cust", "customer@example.com"));
        verify(users, never()).findEligibleRepairClients(any(), any(), any());
    }

    @Test
    void cannotSeeRepairFromAnotherCompany() {
        when(repairs.findByIdAndCompany_Id(1L, 50L)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.get(1L, 50L, "customer@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void rejectsInvalidStatusChange() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.changeStatus(1L, 50L,
                new ChangeRepairStatusRequest(RepairStatus.DELIVERED, "No"),
                "manager@example.com"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(RepairStatus.NEW, repair.getStatus());
    }

    @Test
    void unassignedEmployeeCannotChangeStatus() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.changeStatus(1L, 50L,
                new ChangeRepairStatusRequest(RepairStatus.DIAGNOSING, "Checked"),
                "employee@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void assignmentRequiresMemberInSameCompany() {
        when(users.findById(12L)).thenReturn(Optional.of(employee));
        // No membership in company -> reject the requested assignment.
        when(members.findByCompany_IdAndUser_IdAndActiveTrue(1L, 12L))
            .thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.assign(1L, 50L, new AssignRepairRequest(12L), "manager@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertNull(repair.getAssignedEmployee());
    }

    @Test
    void partIsConsumedAtomicallyAndPriceIsSnapshotted() {
        repair.changeStatus(RepairStatus.DIAGNOSING);
        when(items.lockForUpdate(1L, 8L)).thenReturn(Optional.of(item));
        when(parts.saveAndFlush(any(RepairPart.class))).thenAnswer(inv -> inv.getArgument(0));
        service.addPart(1L, 50L, new AddRepairPartRequest(8L, 2, true), "manager@example.com");
        assertEquals(2, item.getStockQuantity());
        verify(movements).save(argThat(m -> m.getMovementType() == MovementType.REPAIR
            && m.getQuantityChange() == -2));
        verify(parts).saveAndFlush(argThat(p ->
            p.getUnitCostPrice().compareTo(new BigDecimal("50")) == 0 &&
            p.getUnitSellingPrice().compareTo(new BigDecimal("80")) == 0));
    }

    @Test
    void insufficientStockDoesNotCreatePartOrMovement() {
        repair.changeStatus(RepairStatus.IN_PROGRESS);
        when(items.lockForUpdate(1L, 8L)).thenReturn(Optional.of(item));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.addPart(1L, 50L, new AddRepairPartRequest(8L, 5, true), "manager@example.com"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(4, item.getStockQuantity());
        verify(parts, never()).saveAndFlush(any());
        verify(movements, never()).save(any());
    }

    @Test
    void unspecifiedPartRequiresExplicitFitConfirmation() {
        repair.changeStatus(RepairStatus.IN_PROGRESS);
        when(items.lockForUpdate(1L, 8L)).thenReturn(Optional.of(item));
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
            () -> service.addPart(1L, 50L, new AddRepairPartRequest(8L, 1, false), "manager@example.com"));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertEquals(4, item.getStockQuantity());
        verify(parts, never()).saveAndFlush(any());
    }

    @Test
    void knownIncompatiblePartIsRejectedEvenWhenConfirmationIsSent() {
        item.setDeviceClassification("Phone", "Apple", List.of(
            new DeviceCompatibilityInput("Phone", "Apple", "iPhone 16")));
        repair.changeStatus(RepairStatus.IN_PROGRESS);
        when(items.lockForUpdate(1L, 8L)).thenReturn(Optional.of(item));
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
            () -> service.addPart(1L, 50L, new AddRepairPartRequest(8L, 1, true), "manager@example.com"));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(parts, never()).saveAndFlush(any());
    }

    @Test
    void assignedTechnicianCanReturnPartToInventoryAndAuditMovement() {
        repair.changeStatus(RepairStatus.IN_PROGRESS);
        repair.assign(employee);
        RepairPart part = new RepairPart(repair, item, 2);
        org.springframework.test.util.ReflectionTestUtils.setField(part, "id", 77L);
        when(parts.findById(77L)).thenReturn(Optional.of(part));
        when(items.lockForUpdate(1L, 8L)).thenReturn(Optional.of(item));
        service.returnPart(1L, 50L, 77L, "employee@example.com");
        assertEquals(6, item.getStockQuantity());
        verify(parts).delete(part);
        verify(movements).save(argThat(m -> m.getMovementType() == MovementType.RETURN && m.getQuantityChange() == 2));
    }

    @Test
    void cancellationAfterConsumedPartsIsRejected() {
        when(parts.existsByRepair_Id(50L)).thenReturn(true);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.changeStatus(1L, 50L,
                new ChangeRepairStatusRequest(RepairStatus.CANCELLED, "Cancel"),
                "manager@example.com"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(RepairStatus.NEW, repair.getStatus());
    }

    @Test
    void managerSeesCostsButCustomerDoesNot() {
        when(repairs.findByIdAndCompany_Id(50L, 1L)).thenReturn(Optional.of(repair));
        RepairView customerView = service.get(1L, 50L, "customer@example.com");
        assertNull(customerView.laborCost());
        assertNull(customerView.estimatedGrossProfit());
        RepairView managerView = service.get(1L, 50L, "manager@example.com");
        assertNotNull(managerView.laborCost());
    }

    @Test
    void repairProfitUsesPartSnapshotsNotLatestCatalogPrice() {
        RepairPart installed = new RepairPart(repair, item, 2);
        repair.setLabor(new BigDecimal("100.00"), new BigDecimal("35.00"));
        repair.changeStatus(RepairStatus.DIAGNOSING);
        repair.changeStatus(RepairStatus.IN_PROGRESS);
        repair.changeStatus(RepairStatus.COMPLETED);
        item.updateDetails("LCD-1", "Screen", null,
            new BigDecimal("70.00"), new BigDecimal("120.00"));
        when(repairs.findByCompany_IdAndStatusIn(eq(1L), anyList())).thenReturn(List.of(repair));
        when(parts.findByRepair_IdOrderByIdAsc(50L)).thenReturn(List.of(installed));
        RepairProfitSummaryView summary = service.profitSummary(1L, "manager@example.com");
        assertEquals(new BigDecimal("160.00"), summary.partsRevenue());
        assertEquals(new BigDecimal("100.00"), summary.partsCost());
        assertEquals(new BigDecimal("125.00"), summary.estimatedGrossProfit());
    }

    @Test
    void trackingListsOnlyCustomersOwnRepairsAcrossCompanies() {
        when(repairs.findByCustomer_IdOrderByCreatedAtDesc(10L)).thenReturn(List.of(repair));
        List<CustomerRepairTrackingView> result = service.trackingMine("customer@example.com");
        assertEquals(1, result.size());
        assertEquals(50L, result.get(0).id());
        assertEquals("Company One", result.get(0).companyName());
        assertEquals(RepairStatus.NEW, result.get(0).status());
        verify(repairs).findByCustomer_IdOrderByCreatedAtDesc(10L);
    }

    @Test
    void trackingCannotAccessOtherCustomersRepairs() {
        when(repairs.findByIdAndCustomer_Id(50L, 10L)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.trackingOne(50L, "customer@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void trackingNeverIncludesPrivateNotesAndStaffIds() {
        when(repairs.findByIdAndCustomer_Id(50L, 10L)).thenReturn(Optional.of(repair));
        RepairUpdate privateEvent = new RepairUpdate(repair, manager, RepairStatus.NEW,
            "Internal investigation confidential");
        RepairUpdate publicEvent = new RepairUpdate(repair, manager, RepairStatus.DIAGNOSING,
            "Internal diagnostics", "We have started diagnosing your device");
        when(updates.findByRepair_IdOrderByCreatedAtAscIdAsc(50L))
            .thenReturn(List.of(privateEvent, publicEvent));
        CustomerRepairTrackingView result = service.trackingOne(50L, "customer@example.com");
        assertEquals(2, result.timeline().size());
        assertNull(result.timeline().get(0).note());
        assertEquals("We have started diagnosing your device", result.timeline().get(1).note());
        RepairUpdateView customerEvent = RepairUpdateView.of(publicEvent, false);
        assertNull(customerEvent.updatedByUserId());
        assertEquals("We have started diagnosing your device", customerEvent.note());
        assertNull(customerEvent.customerNote());
        RepairUpdateView staffEvent = RepairUpdateView.of(publicEvent, true);
        assertEquals("Internal diagnostics", staffEvent.note());
        assertEquals("We have started diagnosing your device", staffEvent.customerNote());
    }

    @Test
    void managerCanPublishProgressWithoutChangingRepairStatus() {
        service.publishProgress(1L, 50L,
            new PublishRepairProgressRequest("Your device is in the queue"), "manager@example.com");
        assertEquals(RepairStatus.NEW, repair.getStatus());
        verify(updates).save(argThat(u ->
            u.getNewStatus() == RepairStatus.NEW &&
            "Your device is in the queue".equals(u.getCustomerNote()) && u.getNote() == null));
    }

    @Test
    void unassignedEmployeeCannotPublishProgress() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.publishProgress(1L, 50L,
                new PublishRepairProgressRequest("Any update"), "employee@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(updates, never()).save(any());
    }

    @Test
    void completingRepairRequiresAssignedTechnicianAndReport() {
        repair.changeStatus(RepairStatus.IN_PROGRESS);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.changeStatus(1L, 50L,
                new ChangeRepairStatusRequest(RepairStatus.COMPLETED, "Done"), "manager@example.com"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("Assign a technician"));
        assertEquals(RepairStatus.IN_PROGRESS, repair.getStatus());
    }

    @Test
    void completingRepairAfterTechnicianAndSavedReportSucceeds() {
        repair.changeStatus(RepairStatus.IN_PROGRESS);
        repair.assign(employee);
        repair.recordReport("Battery is defective", "Installed battery and passed testing");
        RepairView result = service.changeStatus(1L, 50L,
            new ChangeRepairStatusRequest(RepairStatus.COMPLETED, "Verified"), "manager@example.com");
        assertEquals(RepairStatus.COMPLETED, result.status());
        assertNotNull(repair.getCompletedAt());
        verify(updates).save(argThat(u -> u.getNewStatus() == RepairStatus.COMPLETED));
    }
}
