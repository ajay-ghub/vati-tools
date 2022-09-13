package com.ajay.bio.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;

public class ClustalOmegaClient {
    private static final String BASE_URL = "http://www.ebi.ac.uk/Tools/services/rest/clustalo";
    private static final WebTarget runTarget = ClientBuilder.newClient().target(BASE_URL);

    public static String submitProteinSequenceJob(final List<String> sequenceList) throws IOException {
        return submitJob(sequenceList, "protein");
    }

    public static String submitDNASequenceJob(final List<String> sequenceList) throws IOException {
        return submitJob(sequenceList, "dna");
    }

    private static String submitJob(final List<String> sequenceList, final String sequenceType) throws IOException {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
        final Form form = new Form();
        form.param("sequence", String.join("", sequenceList));
        form.param("email", "nobody@gmail.com");
        form.param("title", "test-run-title");
        form.param("outfmt", "clustal_num");
        form.param("stype", sequenceType);

        try {
            final String jobId = runTarget.path("run")
                                          .request(MediaType.TEXT_PLAIN)
                                          .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:91.0) Gecko/20100101 Firefox/91.0")
                                          .header("Connection", "keep-alive")
                                          .post(Entity.form(form), String.class);
            System.out.println("Clustal Omega alignment Job ID - " + jobId);

            return jobId;
        } catch (BadRequestException e) {
            System.out.println(e.getMessage());
            System.out.println(e.getResponse());
            System.out.println(IOUtils.toString((ByteArrayInputStream) e.getResponse().getEntity(),
                                                StandardCharsets.UTF_8));
        } catch (NotAcceptableException e) {
            System.out.println(e.getMessage());
            System.out.println(e.getResponse());
        }

        return "";
    }

    public static String getJobStatus(final String jobId) {
        return runTarget.path("status/{jobId}")
                        .resolveTemplate("jobId", jobId)
                        .request(MediaType.TEXT_PLAIN)
                        .get(String.class);
    }

    public static String getCompletedJobOutput(final String jobId) {
        return runTarget.path("result/{jobId}/{type}")
                        .resolveTemplate("jobId", jobId)
                        .resolveTemplate("type", "aln-clustal_num")
                        .request(MediaType.APPLICATION_OCTET_STREAM)
                        .get(String.class);
    }
}
