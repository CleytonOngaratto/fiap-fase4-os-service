package br.com.fiap.postech.carworkshop.bdd.e2e;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Optional;
import java.util.function.Function;

final class SagaQueues {

    int waitingOn(String queueUrl) {
        return withClient(sqs -> Integer.parseInt(sqs.getQueueAttributes(request -> request
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                .attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)));
    }

    // Visibilidade zero: le para transcrever a evidencia sem esconder a mensagem do runbook de redrive.
    Optional<Message> peek(String queueUrl) {
        return withClient(sqs -> sqs.receiveMessage(request -> request
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(1)
                        .visibilityTimeout(0))
                .messages().stream().findFirst());
    }

    private static <T> T withClient(Function<SqsClient, T> call) {
        try (SqsClient sqs = SqsClient.builder()
                .region(Region.of(E2eEnvironment.required("AWS_REGION")))
                .httpClient(UrlConnectionHttpClient.create())
                .build()) {
            return call.apply(sqs);
        }
    }
}
