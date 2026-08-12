package app.mildang.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void idHasPrefixAndUlid() {
        String id = Ids.next(Ids.Prefix.CHALLENGE);
        assertThat(id).matches("chl_[0-9A-HJKMNP-TV-Z]{26}");
    }

    @Test
    void idsAreUnique() {
        assertThat(Ids.next(Ids.Prefix.ITEM)).isNotEqualTo(Ids.next(Ids.Prefix.ITEM));
    }
}
