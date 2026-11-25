package com.example.syndicateloan.organization;

import java.util.Collection;
import java.util.Map;

/** 利用組織を、他のモジュールに見せる公開 API。 */
public interface Organizations {

    /** 利用組織の id と会社名。存在しない id は含まない。 */
    Map<Long, String> namesOf(Collection<Long> organizationIds);
}
