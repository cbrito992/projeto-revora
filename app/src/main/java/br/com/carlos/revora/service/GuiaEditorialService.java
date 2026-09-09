package br.com.carlos.revora.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


@Service
public class GuiaEditorialService {

    private final String humanizacao;
    private final String abnt;


    public GuiaEditorialService() {

        this.humanizacao =
                carregar(
                        "regras-editoriais/humanizacao.md"
                );

        this.abnt =
                carregar(
                        "regras-editoriais/abnt.md"
                );
    }


    public String getHumanizacao() {
        return humanizacao;
    }


    public String getAbnt() {
        return abnt;
    }


    private String carregar(
            String caminho
    ) {

        ClassPathResource resource =
                new ClassPathResource(
                        caminho
                );


        try (
                InputStream input =
                        resource.getInputStream()
        ) {

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Não foi possível carregar a regra editorial: "
                            + caminho,
                    e
            );
        }
    }
}