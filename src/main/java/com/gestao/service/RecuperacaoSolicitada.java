package com.gestao.service;
// Contém o segredo somente em memória até o envio. Nunca registrar este evento em logs.
public final class RecuperacaoSolicitada {
    private final String email;
    private final String token;
    public RecuperacaoSolicitada(String email,String token) { this.email=email; this.token=token; }
    public String email() { return email; }
    public String token() { return token; }
}
