package it.uniroma3.sii.service.chat;

import java.util.ArrayList;
import java.util.List;

import it.uniroma3.sii.model.Citation;
import lombok.Data;

@Data
public class StructuredAnswer {
    private String answer;
    private List<Citation> citations = new ArrayList<>();
}
