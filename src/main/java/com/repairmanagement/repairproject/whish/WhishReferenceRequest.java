package com.repairmanagement.repairproject.whish;

import jakarta.validation.constraints.*;

/** Sender-reported contact; authenticated customer ID comes exclusively from the login token. */
public record WhishReferenceRequest(
    @NotBlank @Size(min=5,max=120) String reference,
    @NotBlank @Size(min=2,max=150) String senderName,
    @NotBlank @Pattern(regexp="[+0-9() .\\-]{6,40}",message="Enter a valid Whish sender phone") String senderPhone
) {}
