package com.smxplore.users.application.dto;

import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.UserStatus;
import java.util.Set;

public record UserSearchCriteria(
        String q,
        Role role,
        UserStatus status,
        ProviderRegistrationStatus providerStatus,
        String sortBy,
        SortOrder sortOrder,
        int page,
        int size) {

    public static final int MAX_SIZE = 100;
    public static final String DEFAULT_SORT = "signedUpAt";
    private static final Set<String> SORTABLE =
            Set.of("fullName", "email", "signedUpAt", "updatedAt", "role", "status");

    public UserSearchCriteria {
        if (page < 0) {
            throw new ValidationException("La página no puede ser negativa");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ValidationException("El tamaño de página debe estar entre 1 y " + MAX_SIZE);
        }
        q = q == null || q.isBlank() ? null : q.trim();
        sortBy = sortBy == null || sortBy.isBlank() ? DEFAULT_SORT : sortBy;
        if (!SORTABLE.contains(sortBy)) {
            throw new ValidationException("El campo de ordenamiento no es válido");
        }
        sortOrder = sortOrder == null ? SortOrder.DESC : sortOrder;
    }
}
