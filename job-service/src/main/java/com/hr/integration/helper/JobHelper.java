package com.intalent.integration.helper;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.hr.integration.dto.JobDTO;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.microsoft.azure.functions.ExecutionContext;

/**
 * All Rights reserved @hr.ai - 2024
 * 
 * @author hr
 */
public class JobHelper {

	public String uploadFile(Path filePath, ExecutionContext context) {

		BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
				.connectionString(System.getenv("BLOB-STORAGE-CONNECTION-STRING")).buildClient();

		BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient("demodocs");

		if (!containerClient.exists()) {
			containerClient.create();
		}

		String blobName = "hr-" + filePath.getFileName();
		BlobClient blobClient = containerClient.getBlobClient(blobName);

		try {

			blobClient.uploadFromFile(filePath.toString(), true); // true to overwrite if exists
		} catch (Exception e) {
			context.getLogger().severe("Exception  error when calling JobsApi#jobsList: " + e.getMessage());
		}

		return blobName;
	}

	public Path writeToPdf(JobDTO jobDto, ExecutionContext context) {

		String suffix = System.getenv("FILE-EXTENSION");
		// This will be the file extension

		Path filePath = null;
		try {
			filePath = Files.createTempFile(null, suffix);
		} catch (IOException e) {
			context.getLogger().severe("IOException  error when calling JobsApi#writeToPdf: " + e.getMessage());
		}

		// Create a PDF writer instance
		PdfWriter writer = null;
		try {
			writer = new PdfWriter(new FileOutputStream(filePath.toFile()));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		// Initialize a PDF document
		PdfDocument pdfDoc = new PdfDocument(writer);

		// Initialize a document (layout)
		Document document = new Document(pdfDoc);

		// Add job details to the document
		document.add(new Paragraph("Job ID: " + jobDto.getId()));
		document.add(new Paragraph("Job Name: " + jobDto.getName()));
		document.add(new Paragraph("Job Code: " + jobDto.getCode()));
		document.add(new Paragraph("Job Description: " + jobDto.getDescription()));

		document.close();
		return filePath;

	}


}
