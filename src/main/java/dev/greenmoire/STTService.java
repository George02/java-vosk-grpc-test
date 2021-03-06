package dev.greenmoire;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import vosk.stt.v1.SttServiceGrpc;
import vosk.stt.v1.SttServiceOuterClass;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static vosk.stt.v1.SttServiceOuterClass.*;

public class STTService {

    private final SttServiceGrpc.SttServiceStub sttClient;

    public STTService() {

        ManagedChannel channel = ManagedChannelBuilder.forAddress("172.17.0.2", 5001)
                .usePlaintext()
                .build();

        sttClient = SttServiceGrpc.newStub(channel);
    }

    public void transcribeAudioFile(File file) throws IOException {
        StreamObserver<StreamingRecognitionRequest> serverHandler = sendConfigRequest(UUID.randomUUID());

        System.out.println("New transcribe request " + file.toString());

        StreamingRecognitionRequest request;

        request = StreamingRecognitionRequest.newBuilder()
                .setAudioContent(ByteString.copyFrom(Files.readAllBytes(file.toPath())))
                .build();
        serverHandler.onNext(request);
        serverHandler.onCompleted();

        System.out.println("Audio scheduled for transcribing.");
    }

    public void transcribeAudioFileWithPartialResults(File file) throws IOException {
        int AUDIO_CHUNK_BUFFER_SIZE = 4000;

        StreamObserver<StreamingRecognitionRequest> serverHandler = sendConfigRequest(UUID.randomUUID());

        FileInputStream fileInputStream = new FileInputStream(file);
        FileChannel fileChannel = fileInputStream.getChannel();
        ByteBuffer buffer = ByteBuffer.allocate(AUDIO_CHUNK_BUFFER_SIZE);

        while (fileChannel.read(buffer) > 0) {
            buffer.flip();

            StreamingRecognitionRequest request;

            request = StreamingRecognitionRequest.newBuilder()
                    .setAudioContent(ByteString.copyFrom(Files.readAllBytes(file.toPath())))
                    .build();

            serverHandler.onNext(request);
            serverHandler.onCompleted();
        }

        fileChannel.close();
    }

    private StreamObserver<StreamingRecognitionRequest> sendConfigRequest(UUID requestUUID) {
        StreamObserver<StreamingRecognitionRequest> serverHandler;
        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setSpecification(RecognitionSpec.newBuilder()
                        .setSampleRateHertz(8000)
                        .setAudioEncoding(RecognitionSpec.AudioEncoding.LINEAR16_PCM)
                        .setPartialResults(false)
                        .build())
                .build();

        serverHandler = sttClient.streamingRecognize(handleSpeechRecognitionResult(requestUUID));

        StreamingRecognitionRequest request = StreamingRecognitionRequest.newBuilder()
                .setConfig(config)
                .build();

        serverHandler.onNext(request);

        return serverHandler;
    }

    private StreamObserver<StreamingRecognitionResponse> handleSpeechRecognitionResult(UUID uuid) {
        return new StreamObserver<StreamingRecognitionResponse>() {
            @Override
            public void onNext(StreamingRecognitionResponse streamingRecognitionResponse) {
                System.out.println(streamingRecognitionResponse.toString());
//                for (SpeechRecognitionChunk chunk : streamingRecognitionResponse.getChunksList()) {
//                    boolean isFinal = chunk.getFinal();
//
//                }
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
                System.err.println(throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.println("COMPLETED");
            }
        };
    }

}
