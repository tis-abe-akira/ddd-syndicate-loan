package com.example.syndicateloan.organization.application;

import com.example.syndicateloan.organization.Organizations;
import com.example.syndicateloan.organization.domain.OrganizationRepository;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link Organizations} の実装。 */
@Service
@Transactional(readOnly = true)
public class OrganizationsService implements Organizations {

    private final OrganizationRepository organizations;

    public OrganizationsService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Override
    public Map<Long, String> namesOf(Collection<Long> organizationIds) {
        return organizationIds.isEmpty() ? Map.of() : organizations.findNames(organizationIds);
    }
}
