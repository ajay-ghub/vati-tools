package com.ajay.bio.client;

import java.io.IOException;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

@Log4j2
public class IMGTClient {
    private static final String BASE_URL = "https://www.imgt.org/IMGT_vquest/analysis";
    private static final CloseableHttpClient client = createClient();

    private static CloseableHttpClient createClient() {
        final PoolingHttpClientConnectionManager cm =
                new PoolingHttpClientConnectionManager(ManagedHttpClientConnectionFactory.INSTANCE);
        // Increase max total connection to 200
        cm.setMaxTotal(100);
        // Increase default max connection per route to 50
        cm.setDefaultMaxPerRoute(50);
        return HttpClients.custom().setConnectionManager(cm)
                          .setRetryHandler(new DefaultHttpRequestRetryHandler(3, false))
                          .build();
    }

    public static byte[] getIMGTAnalysisResponse(final String fastaSequence) throws IOException {
        final HttpPost httpPost = new HttpPost(BASE_URL);
        final HttpEntity entity = createMultipartEntity(fastaSequence);
        httpPost.setEntity(entity);

        addHeaders(httpPost);
        try (final CloseableHttpResponse response = client.execute(httpPost)) {
            final byte[] responseContent = IOUtils.toByteArray(response.getEntity().getContent());
            httpPost.releaseConnection();
            return responseContent;
        }
    }

    public static void shutDown() {
        try {
            client.close();
        } catch (IOException e) {
            log.error("HTTP Client shutdown failed");
            e.printStackTrace();
        }
    }

    private static void addHeaders(final HttpPost httpPost) {
        httpPost.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        httpPost.setHeader("Content-type", "multipart/form-data; boundary=---------------------------322339693047834450438456424");
        httpPost.setHeader("Sec-Fetch-Dest", "document");
        httpPost.setHeader("Upgrade-Insecure-Requests", "1");
        httpPost.setHeader("Cookie", "JSESSIONID=D70B18FE8D82922396B09916BA89C87D");
        httpPost.setHeader("Sec-Fetch-Mode", "navigate");
        httpPost.setHeader("Sec-Fetch-Site", "same-origin");
        httpPost.setHeader("Sec-Fetch-User", "?1");
    }

    private static HttpEntity createMultipartEntity(final String fastaSequence) {
        return MultipartEntityBuilder
                .create()
                .setBoundary("---------------------------322339693047834450438456424")
                .addTextBody("species", "human")
                .addTextBody("receptorOrLocusType", "IG")
                .addTextBody("inputType", "inline")
                .addTextBody("sequences", fastaSequence)
                .addTextBody("outputType", "html")
                .addTextBody("dv_V_GENEalignment", "true")
                .addTextBody("resultType", "excel")
                .addTextBody("xv_summary", "true")
                .addTextBody("__checkbox_xv_summary", "true")
                .addTextBody("xv_IMGTgappedNt", "true")
                .addTextBody("__checkbox_xv_IMGTgappedNt", "true")
                .addTextBody("xv_ntseq", "true")
                .addTextBody("__checkbox_xv_ntseq", "true")
                .addTextBody("xv_IMGTgappedAA", "true")
                .addTextBody("__checkbox_xv_IMGTgappedAA", "true")
                .addTextBody("xv_AAseq", "true")
                .addTextBody("__checkbox_xv_AAseq", "true")
                .addTextBody("xv_JUNCTION", "true")
                .addTextBody("__checkbox_xv_JUNCTION", "true")
                .addTextBody("xv_V_REGIONmuttable", "true")
                .addTextBody("__checkbox_xv_V_REGIONmuttable", "true")
                .addTextBody("xv_V_REGIONmutstatsNt", "true")
                .addTextBody("__checkbox_xv_V_REGIONmutstatsNt", "true")
                .addTextBody("xv_V_REGIONmutstatsAA", "true")
                .addTextBody("__checkbox_xv_V_REGIONmutstatsAA", "true")
                .addTextBody("xv_V_REGIONhotspots", "true")
                .addTextBody("__checkbox_xv_V_REGIONhotspots", "true")
                .addTextBody("xv_parameters", "true")
                .addTextBody("__checkbox_xv_parameters", "true")
                .addTextBody("__checkbox_xv_scFv", "true")
                .addTextBody("IMGTrefdirSet", "1")
                .addTextBody("IMGTrefdirAlleles", "true")
                .addTextBody("nbD_GENE", "1")
                .addTextBody("nbVmut", "1")
                .addTextBody("nbDmut", "1")
                .addTextBody("nbJmut", "1")

                .addTextBody("dv_V_GENEalignment", "true")
                .addTextBody("__checkbox_dv_V_GENEalignment", "true")
                .addTextBody("__checkbox_dv_D_GENEalignment", "true")
                .addTextBody("dv_J_GENEalignment", "true")
                .addTextBody("__checkbox_dv_J_GENEalignment", "true")

                .addTextBody("dv_IMGTjctaResults", "true")
                .addTextBody("__checkbox_dv_IMGTjctaResults", "true")

                .addTextBody("dv_eligibleD_GENE", "true")
                .addTextBody("dv_JUNCTIONseq", "true")

                .addTextBody("__checkbox_dv_JUNCTIONseq", "true")
                .addTextBody("dv_V_REGIONalignment", "true")
                .addTextBody("__checkbox_dv_V_REGIONalignment", "true")
                .addTextBody("dv_V_REGIONtranlation", "true")
                .addTextBody("__checkbox_dv_V_REGIONtranlation", "true")
                .addTextBody("dv_V_REGIONprotdisplay", "true")

                .addTextBody("__checkbox_dv_V_REGIONprotdisplay", "true")
                .addTextBody("dv_V_REGIONmuttable", "true")
                .addTextBody("__checkbox_dv_V_REGIONmuttable", "true")
                .addTextBody("dv_V_REGIONmutstats", "true")
                .addTextBody("__checkbox_dv_V_REGIONmutstats", "true")
                .addTextBody("dv_V_REGIONhotspots", "true")

                .addTextBody("__checkbox_dv_V_REGIONhotspots", "true")
                .addTextBody("dv_IMGTgappedVDJseq", "true")
                .addTextBody("__checkbox_dv_IMGTgappedVDJseq", "true")
                .addTextBody("dv_IMGTAutomat", "true")
                .addTextBody("__checkbox_dv_IMGTAutomat", "true")
                .addTextBody("dv_IMGTCollierdePerles", "true")

                .addTextBody("__checkbox_dv_IMGTCollierdePerles", "true")
                .addTextBody("dv_IMGTCollierdePerlesType", "0")
                .addTextBody("sv_V_GENEalignment", "true")
                .addTextBody("__checkbox_sv_V_GENEalignment", "true")
                .addTextBody("sv_V_REGIONalignment", "true")
                .addTextBody("__checkbox_sv_V_REGIONalignment", "true")

                .addTextBody("sv_V_REGIONtranslation", "true")
                .addTextBody("__checkbox_sv_V_REGIONprotdisplay", "true")
                .addTextBody("sv_V_REGIONprotdisplay2", "true")
                .addTextBody("__checkbox_sv_V_REGIONprotdisplay2", "true")
                .addTextBody("sv_V_REGIONprotdisplay3", "true")
                .addTextBody("__checkbox_sv_V_REGIONprotdisplay3", "true")

                .addTextBody("sv_V_REGIONfrequentAA", "true")
                .addTextBody("__checkbox_sv_V_REGIONfrequentAA", "true")
                .addTextBody("sv_IMGTjctaResults", "true")
                .addTextBody("__checkbox_sv_IMGTjctaResults", "true")

                .build();
    }
}
