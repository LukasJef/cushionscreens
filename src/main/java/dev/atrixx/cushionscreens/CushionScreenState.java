package dev.atrixx.cushionscreens;

/**
 * Co potrebujeme ulozit, abychom po restartu serveru / znovu-pripojeni
 * nasli puvodni cushiony a znovu je propojili s modem. Samotne entity
 * (jejich pozice, barva) uz normalne uklada a nacita vanilla Minecraft
 * jako soucast chunku - my si jen potrebujeme pamatovat JEJICH UUID a
 * rozmery/pozici obrazovky, abychom je po nacteni sveta zase nasli.
 */
public final class CushionScreenState {
    public int gridW;
    public int gridH;
    public int originX;
    public int originZ;
    public int cushionY;
    public int viewChunks;
    public String dimension;
    public String[] uuids;
}
