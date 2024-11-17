package com.intalent.integration.service.job_service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.threeten.bp.OffsetDateTime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.integration.dto.JobDTO;
import com.hr.integration.helper.JobHelper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import merge_ats_client.ApiClient;
import merge_ats_client.ApiException;
import merge_ats_client.Configuration;
import merge_ats_client.api.JobsApi;
import merge_ats_client.auth.ApiKeyAuth;
import merge_ats_client.model.Job;
import merge_ats_client.model.PaginatedJobList;

/**
 * All Rights reserved @hr.ai - 2024
 * 
 * @author hr
 */
public class Function {

	@FunctionName("getJobs")
	public HttpResponseMessage run(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
			HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = "hr/v1/jobs") HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) throws IOException {

		context.getLogger().info("GetJobs Function  processed a request.");

		ObjectMapper objectMapper = new ObjectMapper();
		List<String> jobFilesList = new ArrayList<String>();

		Boolean includeDeletedData = true;
		// Whether to include data that was marked as deleted by third party webhooks.
		Boolean includeRemoteData = true;
		// Whether to include the original data Merge fetched from the third-party to

		ApiClient defaultClient = Configuration.getDefaultApiClient();
		defaultClient.setBasePath(System.getenv("MERGE-API-PATH"));
		// Parse query parameter
		final String sourceQueryParameter = request.getQueryParameters().get("source");
		String token = getTokenForSource(sourceQueryParameter, context);
		if (token == null) {
			return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Invalid source or URL").build();
		}

		// to map query parameters
		String code = request.getQueryParameters().get("code");
		String createdBefore = request.getQueryParameters().get("createdBefore");
		String createdAfter = request.getQueryParameters().get("createdAfter");
		String cursor = request.getQueryParameters().get("cursor");
		String modifiedBefore = request.getQueryParameters().get("modifiedBefore");
		String modifiedAfter = request.getQueryParameters().get("modifiedAfter");
		String pageSize = request.getQueryParameters().get("pageSize");
		String remoteFields = request.getQueryParameters().get("remoteFields");
		String remoteId = request.getQueryParameters().get("remoteId");
		String status = request.getQueryParameters().get("status");

		// Initialize the API client and configure authorization
		JobsApi apiInstance = configureApiClient();

		try {
			PaginatedJobList jobResultList = apiInstance.jobsList(token, code,
					createdAfter != null && !createdAfter.isEmpty() ? OffsetDateTime.parse(createdAfter) : null,
					createdBefore != null && !createdBefore.isEmpty() ? OffsetDateTime.parse(createdBefore) : null,
					cursor, includeDeletedData, includeRemoteData,
					modifiedAfter != null && !modifiedAfter.isEmpty() ? OffsetDateTime.parse(modifiedAfter) : null,
					modifiedBefore != null && !modifiedBefore.isEmpty() ? OffsetDateTime.parse(modifiedBefore) : null,
					pageSize != null && !pageSize.isEmpty() ? Integer.parseInt(pageSize) : null, remoteFields, remoteId,
					status);

			context.getLogger().info("Job Result List " + jobResultList);

			for (Job job : jobResultList.getResults()) {
				JobDTO jobDto = new JobDTO(job.getId(), job.getName(), job.getDescription(), job.getCode());
				Path filePath = new JobHelper().writeToPdf(jobDto,context);
				jobFilesList.add(new JobHelper().uploadFile(filePath,context));
			}
		} catch (ApiException e) {
			context.getLogger().severe("ApiException  error when calling JobsApi#jobsList: " + e.getMessage());
			return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Failed to retrieve jobs from API").build();
		}

		try {
			context.getLogger().info("Number of Records : " + jobFilesList.size());
			return request.createResponseBuilder(HttpStatus.OK)
					.body(objectMapper.writeValueAsString(jobFilesList).toString()).build();
		} catch (JsonProcessingException e) {
			context.getLogger()
					.severe("JsonProcessingException  error when calling JobsApi#jobsList: " + e.getMessage());
			return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Failed to retrieve jobs from API").build();
		}

	}

	// configure API client with API key authorization
	private JobsApi configureApiClient() {
		ApiClient defaultClient = Configuration.getDefaultApiClient();
		defaultClient.setBasePath(System.getenv("MERGE-API-PATH"));

		// Configure API key authorization: tokenAuth
		ApiKeyAuth tokenAuth = (ApiKeyAuth) defaultClient.getAuthentication("tokenAuth");
		tokenAuth.setApiKey(System.getenv("AUTH-KEY"));
		tokenAuth.setApiKeyPrefix("Bearer ");

		return new JobsApi(defaultClient);
	}

	// to get source-specific token from environment variables
	private String getTokenForSource(String sourceQueryParameter, ExecutionContext context) {
	    if (sourceQueryParameter == null || sourceQueryParameter.trim().isEmpty()) {
	        context.getLogger().warning("Source parameter is missing or empty");
	        return null;
	    }

	    // Retrieve environment variables
	    String workableToken = System.getenv("WORKABLE-ACCOUNT-TOKEN");
	    String zohoToken = System.getenv("ZOHO-RECRUIT-ACCOUNT-TOKEN");

	    // Trim the sourceQueryParameter to avoid issues with extra spaces
	    String trimmedSource = sourceQueryParameter.trim();

	    // Compare the sourceQueryParameter with known tokens
	    if (trimmedSource.equalsIgnoreCase(zohoToken)) {
	        return zohoToken != null ? zohoToken : null;
	    } else if (trimmedSource.equalsIgnoreCase(workableToken)) {
	        return workableToken != null ? workableToken : null;
	    } else {
	        context.getLogger().warning("Invalid source parameter: " + sourceQueryParameter);
	        return null;
	    }
	}
}
