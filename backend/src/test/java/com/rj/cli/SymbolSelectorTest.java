package com.rj.cli;

import com.rj.config.MarketCategory;
import com.rj.config.SymbolRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class SymbolSelectorTest {

    @Test
    void select_limitZero_returnsAll() {
        SymbolRegistry reg = Mockito.mock(SymbolRegistry.class);
        when(reg.symbolsFor(MarketCategory.CM)).thenReturn(List.of("A", "B", "C"));
        assertThat(SymbolSelector.select(reg, "CM", 0, 0)).containsExactly("A", "B", "C");
    }

    @Test
    void select_offsetAndLimit_applied() {
        SymbolRegistry reg = Mockito.mock(SymbolRegistry.class);
        when(reg.symbolsFor(MarketCategory.CM))
                .thenReturn(List.of("A", "B", "C", "D", "E"));
        assertThat(SymbolSelector.select(reg, "CM", 2, 1)).containsExactly("B", "C");
    }

    @Test
    void select_emptyResult_throws() {
        SymbolRegistry reg = Mockito.mock(SymbolRegistry.class);
        when(reg.symbolsFor(MarketCategory.CM)).thenReturn(List.of());
        assertThatThrownBy(() -> SymbolSelector.select(reg, "CM", 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no symbols");
    }

    @Test
    void select_unknownCategory_throws() {
        SymbolRegistry reg = Mockito.mock(SymbolRegistry.class);
        assertThatThrownBy(() -> SymbolSelector.select(reg, "BOGUS", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
