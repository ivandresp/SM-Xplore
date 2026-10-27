package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySlotDocument {

    private String dayOfWeek;
    private String opensAt;
    private String closesAt;
}
