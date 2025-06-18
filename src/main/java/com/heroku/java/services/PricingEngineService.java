package com.heroku.java.services;

import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.SaveResult;
import com.sforce.soap.partner.sobject.SObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Pricing Engine", description = "Leverage dynamic pricing calculation logic and rules to calculate pricing information.")
@RestController
@RequestMapping("/api/")
public class PricingEngineService {

    private static final Logger logger = LoggerFactory.getLogger(PricingEngineService.class);

    @Value("${ENABLE_DISCOUNT_OVERRIDES:#{false}}")
    private Boolean discountOverridesEnabled;

    @Operation(summary = "Generate a Quote for a given Opportunity", description = "Calculate pricing and generate an associated Quote.")
    @PostMapping("/generatequote")
    public QuoteGenerationResponse generateQuote(
            @RequestBody QuoteGenerationRequest request, HttpServletRequest httpServletRequest) {

        PartnerConnection connection = (PartnerConnection) httpServletRequest.getAttribute("salesforcePartnerConnection");

        try {
            // Construct SOQL query for Opportunity Products
            List<String> fields = new ArrayList<>(List.of("Id", "Product2Id", "Quantity", "UnitPrice", "PricebookEntryId"));
            if (discountOverridesEnabled) {
                fields.add("DiscountOverride__c");
            }
            String soql = String.format(
                "SELECT %s FROM OpportunityLineItem WHERE OpportunityId = '%s'", 
                String.join(", ", fields), 
                request.opportunityId);
            QueryResult queryResult = connection.query(soql);
            if (queryResult.getSize() == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "No OpportunityLineItems found for Opportunity ID: " + request.opportunityId);
            }

            // Default discount rate based on region
            double discountRate = getDiscountForRegion("US"); // Hardcoded region logic for demo

            // Create Quote
            SObject quote = new SObject("Quote");
            quote.setField("Name", "New Quote");
            quote.setField("OpportunityId", request.opportunityId);
            SObject[] quoteRecords = new SObject[]{quote};
            SaveResult[] quoteSaveResults = connection.create(quoteRecords);
            if (!quoteSaveResults[0].isSuccess()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to create Quote: " + quoteSaveResults[0].getErrors()[0].getMessage());
            }
            String quoteId = quoteSaveResults[0].getId();
            logger.info("Quote created with ID: " + quoteId);
            QuoteGenerationResponse response = new QuoteGenerationResponse();
            response.quoteId = quoteId;
            // Prepare QuoteLineItems in bulk
            List<SObject> quoteLineItems = new ArrayList<>();
            for (SObject record : queryResult.getRecords()) {
                double quantity = Double.parseDouble(record.getField("Quantity").toString());
                double unitPrice = Double.parseDouble(record.getField("UnitPrice").toString());
                // Use DiscountOverride__c if available and enabled
                double effectiveDiscountRate = discountRate;
                if (discountOverridesEnabled && record.getField("DiscountOverride__c") != null) {
                    effectiveDiscountRate = Double.parseDouble(record.getField("DiscountOverride__c").toString()) / 100.0;
                }
                // Calculate discount price
                double discountedPrice = (quantity * unitPrice) * (1 - effectiveDiscountRate);
                // Build record to insert
                SObject quoteLineItem = new SObject("QuoteLineItem");
                quoteLineItem.setField("QuoteId", quoteId);
                quoteLineItem.setField("PricebookEntryId", record.getField("PricebookEntryId"));
                quoteLineItem.setField("Quantity", quantity);
                quoteLineItem.setField("UnitPrice", discountedPrice / quantity); // Apply discount per unit price
                quoteLineItems.add(quoteLineItem);
            }
            // Bulk insert QuoteLineItems
            SaveResult[] quoteLineSaveResults = connection.create(quoteLineItems.toArray(new SObject[0]));
            for (SaveResult saveResult : quoteLineSaveResults) {
                if (!saveResult.isSuccess()) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                        "Failed to create QuoteLineItem: " + saveResult.getErrors()[0].getMessage());
                }
            }
            return response;
        } catch (ResponseStatusException e) {
            throw e; // Preserve custom errors with detailed messages
        } catch (Exception e) {
            logger.info("Unexpected error generating quote: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "An unexpected error occurred: " + e.toString());
        }
    }

    private double getDiscountForRegion(String region) {
        // Simple hardcoded discount logic
        switch (region) {
            case "US": return 0.10;
            case "EU": return 0.15;
            case "APAC": return 0.05;
            default: return 0.0;
        }
    }

    @Schema(description = "Request to generate a quote, includes the opportunity ID to extract product information")
    public static class QuoteGenerationRequest {
        @Schema(example = "0065g00000B9tMP", description = "A record Id for the opportunity")
        public String opportunityId;
    }

    @Schema(description = "Response includes the record Id of the generated quote.")
    public static class QuoteGenerationResponse {
        @Schema(example = "0Q05g00000B9tMP", description = "A record Id for the generated quote")
        public String quoteId;
    }
}
