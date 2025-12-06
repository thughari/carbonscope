package com.carbonscope.service;

import com.carbonscope.dto.ChatRequest;
import com.google.genai.Client;
import com.google.genai.types.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GeminiService {

	@Value("${GEMINI_API_KEY}")
	private String apiKey;
	
	@Value("${gemini.model}")
	private String geminiModel;

	private final EmissionAnalyticsService analyticsService;

	public GeminiService(EmissionAnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	public Flux<String> streamResponse(ChatRequest request) {
		return Flux.using(
				() -> Client.builder().apiKey(apiKey).build(),
				client -> {
					GenerateContentConfig config = "web".equals(request.mode()) 
							? buildWebConfig() 
									: buildDataConfig(request.country(), request.continent(), request.year());

					List<Content> fullConversation = buildConversationHistory(request.history(), request.question());

					return Flux.fromIterable(client.models.generateContentStream(geminiModel, fullConversation, config))
							.map(GenerateContentResponse::text)
							.filter(text -> text != null && !text.isBlank());
				},
				Client::close
				).subscribeOn(Schedulers.boundedElastic());
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
						"You are CarbonScope AI. Provide recent climate news using Markdown.")))
				.tools(Tool.builder().googleSearch(GoogleSearch.builder().build()).build())
				.build();
	}

	private GenerateContentConfig buildDataConfig(String country, String continent, Integer year) {
		String dataContext = fetchAndFormatData(country, continent, year);

		String locationLabel = "the World (Global View)";
		if (country != null) locationLabel = "Country: " + country;
		else if (continent != null) locationLabel = "Continent: " + continent;

		String prompt = """
				You are CarbonScope AI, an intelligent analyst for the Climate TRACE dashboard.

				--- CURRENT DASHBOARD STATE ---
				VIEWING: %s
				YEAR: %d

				--- LIVE DATA ---
				%s

				--- INSTRUCTIONS ---
				1. Analyze the provided data to answer the user's question.
				2. CRITICAL: You strictly see data ONLY for the 'VIEWING' location above.
				3. IF THE USER ASKS ABOUT A DIFFERENT REGION (e.g., User asks for "Russia" but you are viewing "India"):
				   - Do NOT say "I don't have data".
				   - Instead, reply: "I am currently analyzing the dashboard view for **%s**. Please select that region in the filters above to load its data."
				4. Use Markdown formatting (bolding, lists) for readability.
				""".formatted(locationLabel, year, dataContext, locationLabel);

		return GenerateContentConfig.builder()
				.systemInstruction(Content.fromParts(Part.fromText(prompt)))
				.build();
	}

	private String fetchAndFormatData(String country, String continent, Integer year) {
		try {
			var response = analyticsService.getSectorSummary(year, country, continent, "co2e_100yr", 5000);

			if (response == null || response.getSectorTotals().isEmpty()) {
				return "NO DATA AVAILABLE. (Try changing filters)";
			}

			double total = response.getSectorTotals().values().stream().mapToDouble(d -> d).sum();

			return response.getSectorTotals().entrySet().stream()
					.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
					.map(e -> formatLine(e, total))
					.collect(Collectors.joining("\n"));
		} catch (Exception e) {
			return "Error retrieving dataset: " + e.getMessage();
		}
	}

	private String formatLine(Map.Entry<String, Double> entry, double total) {
		double val = entry.getValue();
		return String.format("- **%s**: %.2f Mt (%.1f%%)", 
				entry.getKey(), val / 1_000_000, (val / total) * 100);
	}
}