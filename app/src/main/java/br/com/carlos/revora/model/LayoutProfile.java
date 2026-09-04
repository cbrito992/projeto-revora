package br.com.carlos.revora.model;

public class LayoutProfile {

    private double larguraPaginaCm;
    private double alturaPaginaCm;

    private double margemSuperiorCm;
    private double margemInferiorCm;
    private double margemEsquerdaCm;
    private double margemDireitaCm;

    private boolean paisagem;


    public double getLarguraPaginaCm() {
        return larguraPaginaCm;
    }

    public void setLarguraPaginaCm(double larguraPaginaCm) {
        this.larguraPaginaCm = larguraPaginaCm;
    }


    public double getAlturaPaginaCm() {
        return alturaPaginaCm;
    }

    public void setAlturaPaginaCm(double alturaPaginaCm) {
        this.alturaPaginaCm = alturaPaginaCm;
    }


    public double getMargemSuperiorCm() {
        return margemSuperiorCm;
    }

    public void setMargemSuperiorCm(double margemSuperiorCm) {
        this.margemSuperiorCm = margemSuperiorCm;
    }


    public double getMargemInferiorCm() {
        return margemInferiorCm;
    }

    public void setMargemInferiorCm(double margemInferiorCm) {
        this.margemInferiorCm = margemInferiorCm;
    }


    public double getMargemEsquerdaCm() {
        return margemEsquerdaCm;
    }

    public void setMargemEsquerdaCm(double margemEsquerdaCm) {
        this.margemEsquerdaCm = margemEsquerdaCm;
    }


    public double getMargemDireitaCm() {
        return margemDireitaCm;
    }

    public void setMargemDireitaCm(double margemDireitaCm) {
        this.margemDireitaCm = margemDireitaCm;
    }


    public boolean isPaisagem() {
        return paisagem;
    }

    public void setPaisagem(boolean paisagem) {
        this.paisagem = paisagem;
    }
}