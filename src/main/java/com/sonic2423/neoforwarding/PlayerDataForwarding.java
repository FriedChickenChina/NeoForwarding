package com.sonic2423.neoforwarding;

import com.google.common.net.InetAddresses;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.sonic2423.neoforwarding.Config.forwardingSecret;

/*
 * The following is ported from "Paper" with slight modifications to work with NeoForge as Mixin.
 * See: https://github.com/PaperMC/Paper/blob/bd5867a96f792f0eb32c1d249bb4bbc1d8338d14/patches/server/0748-Add-Velocity-IP-Forwarding-Support.patch
 * PlayerDataForwarding: Lines 51-107.
 */
public class PlayerDataForwarding {
    private static final int SUPPORTED_FORWARDING_VERSION = 1;
    public static final int MODERN_FORWARDING_WITH_KEY = 2;
    public static final int MODERN_FORWARDING_WITH_KEY_V2 = 3;
    public static final int MODERN_LAZY_SESSION = 4;
    public static final byte MAX_SUPPORTED_FORWARDING_VERSION = MODERN_LAZY_SESSION;
    public static final ResourceLocation PLAYER_INFO_CHANNEL = ResourceLocation.fromNamespaceAndPath("velocity", "player_info");

    public static boolean checkIntegrity(final FriendlyByteBuf buf) {

        final byte[] signature = new byte[32];
        buf.readBytes(signature);

        final byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);

        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(forwardingSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            final byte[] mySignature = mac.doFinal(data);
            if (!MessageDigest.isEqual(signature, mySignature)) {
                return false;
            }
        } catch (final InvalidKeyException | NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }

        return true;
    }

    public static InetAddress readAddress(final FriendlyByteBuf buf) {
        return InetAddresses.forString(buf.readUtf(Short.MAX_VALUE));
    }

    public static GameProfile createProfile(final FriendlyByteBuf buf) {
        final GameProfile profile = new GameProfile(buf.readUUID(), buf.readUtf(16));
        readProperties(buf, profile);
        return profile;
    }

    private static void readProperties(final FriendlyByteBuf buf, final GameProfile profile) {
        final int properties = buf.readVarInt();
        for (int i1 = 0; i1 < properties; i1++) {
            final String name = buf.readUtf(Short.MAX_VALUE);
            final String value = buf.readUtf(Short.MAX_VALUE);
            final String signature = buf.readBoolean() ? buf.readUtf(Short.MAX_VALUE) : null;
            profile.getProperties().put(name, new Property(name, value, signature));
        }
    }

    public record VelocityPlayerInfoPayload(FriendlyByteBuf buffer) implements CustomQueryPayload {

        public static final ResourceLocation id = PLAYER_INFO_CHANNEL;

        @Override
        public void write(final FriendlyByteBuf buf) {
            buf.writeBytes(this.buffer);
        }

        @Override
        public @NotNull ResourceLocation id() {
            return id;
        }
    }

    public record QueryAnswerPayload(FriendlyByteBuf buffer) implements CustomQueryAnswerPayload {

        @Override
        public void write(final FriendlyByteBuf buf) {
            buf.writeBytes(this.buffer);
        }
    }
}