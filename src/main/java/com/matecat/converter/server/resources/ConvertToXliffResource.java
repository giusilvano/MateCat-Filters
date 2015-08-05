package com.matecat.converter.server.resources;

import com.matecat.converter.core.XliffGenerator;
import com.matecat.converter.core.format.FormatNotSupportedException;
import com.matecat.converter.core.project.Project;
import com.matecat.converter.core.project.ProjectFactory;
import com.matecat.converter.server.exceptions.ServerException;
import com.matecat.converter.server.JSONResponseFactory;
import org.apache.commons.io.FilenameUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.MissingResourceException;


/**
 * Resource taking care of the conversion task into .XLF
 *
 * The call is:
 * [POST]: /convert/{source-language}/{target-language}
 *
 * Sending the file to convert as POST body with multipart/form-data encoding
 *
 * The result is returned as JSON, to obey the old Matecat library (TODO replace for file content)
 */
@Path("/convert")
public class ConvertToXliffResource {

    // Logger
    private static Logger LOGGER = LoggerFactory.getLogger(ConvertToXliffResource.class);

    /**
     * Convert a file into XLF
     * @param sourceLanguageCode Source language
     * @param targetLanguageCode target language
     * @param fileInputStream Input file
     * @param contentDispositionHeader Inputfile
     * @return Converted file
     */
    @POST
    @Path("/{source-lang}/{target-lang}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json")
    public Response convert(
            @PathParam("source-lang") String sourceLanguageCode,
            @PathParam("target-lang") String targetLanguageCode,
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition contentDispositionHeader) {

        // Filename and logging
        String filename = FilenameUtils.getName(contentDispositionHeader.getFileName());
        LOGGER.info("[CONVERSION REQUEST] {}: {} to {}", filename, sourceLanguageCode, targetLanguageCode);

        Project project = null;
        Response response = null;
        try {

            // Check that the input file is not null
            if (fileInputStream == null)
                throw new IllegalArgumentException("The input file has not been sent");

            // Parse the codes
            Locale sourceLanguage = parseLanguage(sourceLanguageCode);
            Locale targetLanguage = parseLanguage(targetLanguageCode);

            // Create the project
            project = ProjectFactory.createProject(filename, fileInputStream);

            // Retrieve the xlf
            File xlf = new XliffGenerator(sourceLanguage, targetLanguage, project.getFile()).generate();

            // Create response
            response = Response
                    .status(Response.Status.OK)
                    .entity(JSONResponseFactory.getSuccess(xlf))
                    .build();
            LOGGER.info("[CONVERSION REQUEST FINISHED]");
        }

        // If there is any error, return it
        catch (Exception e) {
            response =  Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(JSONResponseFactory.getError(e.getMessage()))
                    .build();
            if (e instanceof FormatNotSupportedException)
                LOGGER.error("[CONVERSION REQUEST FAILED] {}", e.getMessage());
            else
                LOGGER.error("[CONVERSION REQUEST FAILED] {}", e.getMessage(), e);
        }

        // Close the project and streams
        finally {
            if (fileInputStream != null)
                try {
                    fileInputStream.close();
                } catch (IOException ignored) {}
            if (project != null)
                project.close();
        }

        return response;
    }


    /**
     * Parse the language code into Locales
     * @param languageCode Language code
     * @return Locale which corresponds to the code
     * @throws ServerException If the code is not valid
     */
    private Locale parseLanguage(String languageCode) throws ServerException {

        // Parse the code
        Locale language = Locale.forLanguageTag(
                languageCode);

        // Validate language
        try {
            language.getISO3Language();
            return language;
        }

        // If there is any error, throw a ServerException
        catch (MissingResourceException e) {
            throw new ServerException("The language '" + languageCode + "' is not valid");
        }
    }

}
