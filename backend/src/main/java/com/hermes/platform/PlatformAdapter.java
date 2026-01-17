package com.hermes.platform;

import com.hermes.domain.model.CreatorProfile;
import java.util.List;

public interface PlatformAdapter {
    String getPlatformName();
    List<CreatorProfile> searchCreators(String query, int limit);
}
