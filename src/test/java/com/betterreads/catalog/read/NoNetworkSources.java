package com.betterreads.catalog.read;

import com.betterreads.catalog.service.pipeline.DescriptionSelector;
import com.betterreads.catalog.service.pipeline.SourceCollector;
import com.betterreads.catalog.service.source.merge.SourceMerger;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the source collector with one that re-merges only the staged data, no network. */
@TestConfiguration
class NoNetworkSources {

    @Bean
    @Primary
    SourceCollector noNetworkSourceCollector(final SourceMerger merger) {
        return new SourceCollector(merger, List.of(), new DescriptionSelector(List.of()), Runnable::run);
    }
}
