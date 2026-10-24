package com.smxplore.users.identity.domain.model;

public sealed interface Profile
        permits TouristProfile, GuideProfile, ProviderProfile, DestinationManagerProfile, AdminProfile {

    Role role();
}
