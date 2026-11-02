package com.repairmanagement.repairproject.whish;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WhishView(Long id,Long orderId,Long invoiceId,Long customerId,String clientAccountName,
    String senderName,String senderPhone,String reference,
    BigDecimal amount,String currencyCode,String receiverPhone,WhishStatus status,
    LocalDateTime submittedAt,LocalDateTime reviewedAt,String reviewNote,boolean recordedOnInvoice) {
  public static WhishView from(WhishTransfer t){return new WhishView(t.getId(),
      t.getOrder()==null?null:t.getOrder().getId(),
      t.getInvoice()==null?null:t.getInvoice().getId(),t.getCustomer().getId(),
      t.getCustomer().getFullName(),t.getSenderName(),t.getSenderPhone(),
      t.getTransferReference(),t.getAmount(),t.getCurrencyCode(),t.getReceiverPhoneSnapshot(),
      t.getStatus(),t.getSubmittedAt(),t.getReviewedAt(),t.getReviewNote(),t.getReconciledPayment()!=null);}
}
