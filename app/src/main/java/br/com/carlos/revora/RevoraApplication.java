package br.com.carlos.revora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RevoraApplication {

    public static void main(String[] args) {
        // Desativa completamente as travas de limite de XML do Java
        // Permitindo que o LanguageTool carregue o dicionário robusto de português sem travar
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
        System.setProperty("jdk.xml.entityExpansionLimit", "0");

        SpringApplication.run(RevoraApplication.class, args);
    }
}