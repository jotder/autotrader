package com.rj.engine;

import com.rj.broker.IMarketDataAdapter;
import com.rj.model.Candle;
import com.tts.in.model.StockHistoryModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandleDownloaderTest {

    @Mock
    private IMarketDataAdapter adapter;

    @Mock
    private CandleDatabase db;

    private CandleDownloader downloader;

    @BeforeEach
    void setup() {
        downloader = new CandleDownloader(adapter, db);
    }

    @Test
    void download_skipsDateAlreadyInDatabase() {
        LocalDate date = LocalDate.of(2026, 1, 2);
        when(db.exists("NSE:SBIN-EQ", date)).thenReturn(true);

        downloader.download("NSE:SBIN-EQ", date, date);

        verifyNoInteractions(adapter);
    }

    @Test
    void download_callsAdapterWithCorrectSymbolAndResolution() {
        LocalDate date = LocalDate.of(2026, 1, 2);
        when(db.exists("NSE:SBIN-EQ", date)).thenReturn(false);
        when(adapter.getHistory(any())).thenReturn(List.of());

        downloader.download("NSE:SBIN-EQ", date, date);

        ArgumentCaptor<StockHistoryModel> captor = ArgumentCaptor.forClass(StockHistoryModel.class);
        verify(adapter).getHistory(captor.capture());
        StockHistoryModel model = captor.getValue();
        assertThat(model.Symbol).isEqualTo("NSE:SBIN-EQ");
        assertThat(model.Resolution).isEqualTo("1");
        assertThat(model.RangeFrom).isNotBlank();
        assertThat(model.DateFormat).isEqualTo("0");
    }
}
