/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.services;

import com.cevapinxile.cestereg.configs.AssetProperties;
import com.cevapinxile.cestereg.interfaces.interfaces.PjesmaInterface;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 *
 * @author denijal
 */
@Service
public class PjesmaService implements PjesmaInterface{

    private final Path basePath;

    public PjesmaService(AssetProperties props) {
        this.basePath = Path.of(props.getBaseDir()).toAbsolutePath().normalize();
    }


    @Override
    public byte[] play(UUID pjesma_id) throws IOException {
        Path snippetPath = basePath.resolve("audio/snippets").resolve(pjesma_id.toString() + ".mp3");
        return Files.readAllBytes(snippetPath);
    }  

    @Override
    public byte[] reveal(UUID pjesma_id) throws IOException {
        Path answerPath = basePath.resolve("audio/answers").resolve(pjesma_id.toString() + ".mp3");
        return Files.readAllBytes(answerPath);
    }
}
