package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.data.TestTokenDAO;
import io.hyperfoil.tools.horreum.api.data.TestToken;

public class TestTokenMapper {
    public static TestToken from(TestTokenDAO tt) {
        TestToken dto = new TestToken();
        dto.id = tt.id;
        dto.testId = tt.test.id;
        dto.permissions = tt.permissions;
        dto.description = tt.description;
        dto.setValue(tt.getValue());

        return dto;
    }

    public static TestTokenDAO to(TestToken dto) {
        TestTokenDAO tt = new TestTokenDAO();
        tt.id = dto.id;
        //tt.test.id = dto.testId; these are lazy loaded so ignoring for now
        tt.permissions = dto.permissions;
        tt.description = dto.description;
        tt.setValue(dto.getValue());

        return tt;
    }
}
