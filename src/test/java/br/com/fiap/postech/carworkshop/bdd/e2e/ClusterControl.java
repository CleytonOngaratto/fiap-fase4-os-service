package br.com.fiap.postech.carworkshop.bdd.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClusterControl {

    record Workload(String namespace, String deployment) {

        static Workload named(String namespaceVariable, String deploymentVariable) {
            return new Workload(E2eEnvironment.required(namespaceVariable), E2eEnvironment.required(deploymentVariable));
        }
    }

    private final Map<Workload, String> takenDown = new LinkedHashMap<>();

    void takeDown(Workload workload) {
        String replicas = kubectl("-n", workload.namespace(), "get", "deployment", workload.deployment(),
                "-o", "jsonpath={.spec.replicas}");
        takenDown.put(workload, replicas);
        scale(workload, "0");
        kubectl("-n", workload.namespace(), "wait", "--for=delete", "pod", "-l", "app=" + workload.deployment(),
                "--timeout=180s");
    }

    void bringBack(Workload workload) {
        String replicas = takenDown.remove(workload);
        if (replicas == null) {
            throw new IllegalStateException(workload + " nao foi derrubado por este cenario.");
        }
        scale(workload, replicas);
        kubectl("-n", workload.namespace(), "rollout", "status", "deployment/" + workload.deployment(),
                "--timeout=300s");
    }

    void bringEverythingBack() {
        new ArrayList<>(takenDown.keySet()).forEach(this::bringBack);
    }

    private static void scale(Workload workload, String replicas) {
        kubectl("-n", workload.namespace(), "scale", "deployment/" + workload.deployment(),
                "--replicas=" + replicas);
    }

    private static String kubectl(String... arguments) {
        List<String> command = new ArrayList<>(List.of("kubectl"));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException(String.join(" ", command) + " saiu com " + exit + ": " + output);
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("kubectl nao rodou: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("kubectl interrompido: " + String.join(" ", command), e);
        }
    }
}
