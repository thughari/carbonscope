package com.carbonscope.service;

import com.carbonscope.client.ClimateTraceClient;
import com.carbonscope.dto.ChatRequest;
import com.carbonscope.dto.EmissionsOverviewResponse;
import com.carbonscope.dto.TrendPointDto;
import com.google.genai.Client;
import com.google.genai.types.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GeminiService {

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    private final EmissionAnalyticsService analyticsService;

    public GeminiService(EmissionAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public Flux<String> streamResponse(ChatRequest request) {
        if (apiKey == null || apiKey.isBlank() || "test_key".equalsIgnoreCase(apiKey)) {
            return Flux.just(generateLocalFallbackResponse(request));
        }

        return Flux.defer(() -> {
            try {
                Client client = Client.builder().apiKey(apiKey).build();
                GenerateContentConfig config = "web".equals(request.mode())
                        ? buildWebConfig()
                        : buildDataConfig(request.country(), request.continent(), request.year());

                List<Content> fullConversation = buildConversationHistory(request.history(), request.question());

                return Flux.fromIterable(client.models.generateContentStream(geminiModel, fullConversation, config))
                        .map(GenerateContentResponse::text)
                        .filter(text -> text != null && !text.isBlank())
                        .doFinally(signalType -> {
                            try {
                                client.close();
                            } catch (Exception ignored) {}
                        });
            } catch (Exception e) {
                log.warn("Gemini API call failed, using intelligent data synthesis: {}", e.getMessage());
                return Flux.just(generateLocalFallbackResponse(request));
            }
        }).onErrorResume(e -> {
            log.warn("Error during Gemini stream: {}", e.getMessage());
            return Flux.just(generateLocalFallbackResponse(request));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private List<Content> buildConversationHistory(List<ChatRequest.HistoryItem> history, String currentQuestion) {
        List<Content> contents = new ArrayList<>();
        if (history != null) {
            for (ChatRequest.HistoryItem item : history) {
                if (item.text() == null || item.text().isEmpty()) continue;
                String role = "user".equalsIgnoreCase(item.role()) ? "user" : "model";
                contents.add(Content.builder().role(role).parts(List.of(Part.fromText(item.text()))).build());
            }
        }
        contents.add(Content.builder().role("user").parts(List.of(Part.fromText(currentQuestion))).build());
        return contents;
    }

    private GenerateContentConfig buildWebConfig() {
        return GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(
                        "You are CarbonScope AI, an expert climate and carbon policy analyst. " +
                        "Provide up-to-date climate intelligence, emission reduction policies, renewable energy progress, " +
                        "and international decarbonization trends using clear, structured Markdown.")))
                .tools(Tool.builder().googleSearch(GoogleSearch.builder().build()).build())
                .build();
    }

    private GenerateContentConfig buildDataConfig(String country, String continent, Integer year) {
        int finalYear = ClimateTraceClient.resolveYear(year);
        EmissionsOverviewResponse overview = analyticsService.getEmissionsOverview(finalYear, country, continent, "co2e_100yr");

        String locationLabel = overview.getCountryName() != null ? overview.getCountryName() : "the World (Global View)";
        String rankStr = overview.getCountryRank() != null ? "#" + overview.getCountryRank() + " Globally" : "Global Overview";

        StringBuilder sectorsText = new StringBuilder();
        if (overview.getSectorTotalsMt() != null) {
            double totalMt = overview.getTotalEmissionsMt();
            overview.getSectorTotalsMt().forEach((sec, valMt) -> {
                double pct = (totalMt > 0) ? (valMt / totalMt) * 100.0 : 0.0;
                sectorsText.append(String.format("- **%s**: %.2f Mt (%.1f%%)\n", sec, valMt, pct));
            });
        }

        StringBuilder trendText = new StringBuilder();
        if (overview.getHistoricalTrend() != null) {
            for (TrendPointDto pt : overview.getHistoricalTrend()) {
                trendText.append(String.format("  * %d: %.1f Mt (%.2f%% of world)\n", pt.getYear(), pt.getEmissionsMt(), pt.getShareOfGlobal()));
            }
        }

        String prompt = """
                You are CarbonScope AI, a senior environmental data analyst powered by verified multi-source carbon data.

                --- VERIFIED MULTI-SOURCE CONTEXT ---
                LOCATION: %s (%s)
                ANALYSIS YEAR: %d
                GLOBAL RANK: %s
                TOTAL EMISSIONS: %.2f Mt CO2e (%.2f%% of Global Emissions)
                GLOBAL TOTAL: %.2f Mt CO2e
                PER-CAPITA FOOTPRINT: %.2f Tonnes/person (World Benchmark: 4.7 t/person)
                POWER GRID CARBON INTENSITY: %s
                CLEAN ELECTRICITY SHARE: %s

                --- VERIFIED DATA SOURCES ---
                1. Climate TRACE (Satellite observations & AI facility tracking)
                2. Global Carbon Project (GCP) & Our World in Data (Per-capita & cumulative budgets)
                3. Ember Climate (Electricity sector and grid carbon intensity)
                4. EDGAR (European Commission JRC inventory baseline)

                --- SECTORAL BREAKDOWN ---
                %s

                --- HISTORICAL TREND (2015-Present) ---
                %s

                --- INSTRUCTIONS ---
                1. Answer the user's questions clearly, authoritatively, and concisely using Markdown.
                2. Cite specific numbers (Mt, percentages, per-capita, sector rankings) from the verified dataset above.
                3. Highlight decarbonization priorities, power sector intensity, and high-impact mitigation opportunities.
                4. Maintain scientific accuracy and actionable clarity.
                """.formatted(
                locationLabel, overview.getCountry() != null ? overview.getCountry() : "Global",
                finalYear, rankStr, overview.getTotalEmissionsMt(), overview.getPercentageOfGlobal(),
                overview.getWorldEmissionsMt(), overview.getPerCapitaTonnes(),
                overview.getGridCarbonIntensityGco2PerKwh() != null ? overview.getGridCarbonIntensityGco2PerKwh() + " gCO2/kWh" : "N/A",
                overview.getCleanPowerPercentage() != null ? overview.getCleanPowerPercentage() + "%" : "N/A",
                sectorsText.toString(),
                trendText.toString()
        );

        return GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(prompt)))
                .build();
    }

    private String generateLocalFallbackResponse(ChatRequest request) {
        int year = ClimateTraceClient.resolveYear(request.year());
        EmissionsOverviewResponse overview = analyticsService.getEmissionsOverview(year, request.country(), request.continent(), "co2e_100yr");

        String loc = overview.getCountryName() != null ? overview.getCountryName() : "Global";
        String rank = overview.getCountryRank() != null ? ("#" + overview.getCountryRank() + " globally") : "Global Summary";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### 🌍 CarbonScope Climate Analysis: %s (%d)\n\n", loc, year));
        sb.append(String.format("- **Total Carbon Footprint**: **%.2f Mt CO₂e** (%s)\n", overview.getTotalEmissionsMt(), rank));
        sb.append(String.format("- **Global Share**: **%.2f%%** of world emissions (World Total: %.1f Mt)\n", overview.getPercentageOfGlobal(), overview.getWorldEmissionsMt()));
        sb.append(String.format("- **Per-Capita Impact**: **%.2f t/person** (Global average is 4.7 t/person)\n", overview.getPerCapitaTonnes()));

        if (overview.getGridCarbonIntensityGco2PerKwh() != null) {
            sb.append(String.format("- **Power Grid Intensity**: **%.0f gCO₂/kWh** with **%.1f%% clean electricity** (Ember Climate data)\n",
                    overview.getGridCarbonIntensityGco2PerKwh(), overview.getCleanPowerPercentage()));
        }

        sb.append("\n#### 🏭 Key Sector Contributions:\n");
        if (overview.getSectorTotalsMt() != null && !overview.getSectorTotalsMt().isEmpty()) {
            overview.getSectorTotalsMt().entrySet().stream().limit(4).forEach(e -> {
                double pct = (overview.getTotalEmissionsMt() > 0) ? (e.getValue() / overview.getTotalEmissionsMt()) * 100 : 0;
                sb.append(String.format("* **%s**: %.1f Mt (%.1f%%)\n", capitalize(e.getKey()), e.getValue(), pct));
            });
        }

        sb.append("\n> **Verified Multi-Source Consensus**: Data compiled from *Climate TRACE* (facility & national satellite observation), *Global Carbon Project / Our World in Data* (per-capita benchmarks), and *Ember Climate* (grid intensity).");

        return sb.toString();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace('-', ' ');
    }
}