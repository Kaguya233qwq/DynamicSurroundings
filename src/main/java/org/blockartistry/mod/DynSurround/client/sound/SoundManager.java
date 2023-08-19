/*
 * This file is part of Dynamic Surroundings, licensed under the MIT License (MIT).
 *
 * Copyright (c) OreCruncher
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.blockartistry.mod.DynSurround.client.sound;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.blockartistry.mod.DynSurround.ModLog;
import org.blockartistry.mod.DynSurround.ModOptions;
import org.blockartistry.mod.DynSurround.client.EnvironStateHandler.EnvironState;
import org.blockartistry.mod.DynSurround.compat.BlockPos;
import org.blockartistry.mod.DynSurround.data.SoundRegistry;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundPoolEntry;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;

@SideOnly(Side.CLIENT)
public class SoundManager {

	private static final int AGE_THRESHOLD_TICKS = 5;
	private static final int SOUND_QUEUE_SLACK = 6;
	private static final Map<SoundEffect, Emitter> emitters = new HashMap<>();
	private static final List<SpotSound> pending = new ArrayList<>();

	private static int normalChannelCount = 0;
	private static int streamChannelCount = 0;

	public static void clearSounds() {
		for (final Emitter emit : emitters.values())
			emit.fade();
		emitters.clear();
		pending.clear();
	}

	public static void queueAmbientSounds(final List<SoundEffect> sounds) {
		// Need to remove sounds that are active but not
		// in the incoming list
		final List<SoundEffect> active = new ArrayList<>(emitters.keySet());
		for (final SoundEffect effect : active) {
			if (!sounds.contains(effect))
				emitters.remove(effect).fade();
			else {
				final Emitter emitter = emitters.get(effect);
				SoundEffect incoming = null;
				for (final SoundEffect sound : sounds)
					if (sound.equals(effect)) {
						incoming = sound;
						break;
					}
				emitter.setVolume(incoming.getVolume());
				sounds.remove(effect);
			}
		}

		// Add sounds from the incoming list that are not
		// active.
		for (final SoundEffect sound : sounds)
			emitters.put(sound, new Emitter(sound));
	}

	public static void update() {
		for (final Emitter emitter : emitters.values())
			emitter.update();

		final Iterator<SpotSound> pitr = pending.iterator();
		while (pitr.hasNext()) {
			final SpotSound sound = pitr.next();
			if (sound.getTickAge() >= AGE_THRESHOLD_TICKS) {
				ModLog.debug("AGING: " + sound);
				pitr.remove();
			} else if (sound.getTickAge() >= 0 && canFitSound()) {
				playSound(sound);
				pitr.remove();
			}
		}
	}

	public static int currentSoundCount() {
		return Minecraft.getMinecraft().getSoundHandler().sndManager.playingSounds.size();
	}

	public static int maxSoundCount() {
		return SoundSystemConfig.getNumberNormalChannels() + SoundSystemConfig.getNumberStreamingChannels();
	}

	private static boolean canFitSound() {
		return currentSoundCount() < (SoundSystemConfig.getNumberNormalChannels() - SOUND_QUEUE_SLACK);
	}

	// ASM hook for SoundManager::playSound
	public static void flushSound() {
		final SoundHandler h = Minecraft.getMinecraft().getSoundHandler();
		((SoundSystem) h.sndManager.sndSystem).CommandQueue(null);
	}

	static void playSound(final ISound sound) {
		if (sound != null) {
			if (ModOptions.enableDebugLogging)
				ModLog.debug("PLAYING: " + sound);
			final SoundHandler h = Minecraft.getMinecraft().getSoundHandler();
			h.playSound(sound);
		}
	}

	public static void playSoundAtPlayer(final SoundEffect sound) {
		final SpotSound s = new SpotSound(sound);
		playSound(s);
	}

	public static void playSoundAtPlayer(EntityPlayer player, final SoundEffect sound) {

		if (player == null)
			player = EnvironState.getPlayer();

		final SpotSound s = new SpotSound(player, sound);

		if (!canFitSound())
			pending.add(s);
		else
			playSound(s);
	}

	public static void playSoundAt(final BlockPos pos, final SoundEffect sound, final int tickDelay) {
		if (tickDelay > 0 && !canFitSound())
			return;

		final SpotSound s = new SpotSound(pos, sound, tickDelay);

		if (tickDelay > 0 || !canFitSound())
			pending.add(s);
		else
			playSound(s);
	}

	public static boolean isSoundPlaying(@Nonnull final ISound sound) {
		final net.minecraft.client.audio.SoundManager manager = Minecraft.getMinecraft().getSoundHandler().sndManager;
		return manager.isSoundPlaying(sound) || manager.playingSounds.containsValue(sound)
				|| manager.delayedSounds.containsKey(sound);
	}

	public static List<String> getSounds() {
		final List<String> result = new ArrayList<>();
		for (final SoundEffect effect : emitters.keySet())
			result.add("EMITTER: " + effect.toString() + "[vol:" + emitters.get(effect).getVolume() + "]");
		for (final SpotSound effect : pending)
			result.add((effect.getTickAge() < 0 ? "DELAYED: " : "PENDING: ") + effect.getSoundEffect().toString());
		return result;
	}

	private static float getVolume(@Nonnull final SoundCategory category) {
		final GameSettings settings = Minecraft.getMinecraft().gameSettings;
		return settings != null && category != SoundCategory.MASTER
				? settings.getSoundLevel(category)
				: 1.0F;
	}

	// Redirect via ASM
	public static float getNormalizedVolume(final ISound sound, final SoundPoolEntry poolEntry,
			final SoundCategory category) {
		float result = 0.0F;
		if (sound == null) {
			ModLog.warn("getNormalizedVolume(): Null sound parameter");
			return result;
		}

		final String soundName = sound.getPositionedSoundLocation().toString();
		if (poolEntry == null) {
			ModLog.warn("getNormalizedVolume(%s): Null poolEntry parameter", soundName);
		} else if (category == null) {
			ModLog.warn("getNormalizedVolume(%s): Null category parameter", soundName);
		} else {
			try {
				final float volumeScale = SoundRegistry.getVolumeScale(soundName);
				result = (float) MathHelper.clamp_double(
						sound.getVolume() * poolEntry.getVolume() * getVolume(category) * volumeScale, 0.0D, 1.0D);
			} catch (final Throwable t) {
				ModLog.error("getNormalizedVolume(): Unable to calculate " + soundName, t);
			}
		}
		return result;
	}

	// Redirect via ASM
	public static float getNormalizedPitch(final ISound sound, final SoundPoolEntry poolEntry) {
		float result = 0F;
		if (sound == null) {
			ModLog.warn("getNormalizedPitch(): Null sound parameter");
			return result;
		}

		final String soundName = sound.getPositionedSoundLocation().toString();
		if (poolEntry == null) {
			ModLog.warn("getNormalizedPitch(%s): Null poolEntry parameter", soundName);
		} else {
			try {
				result = (float) MathHelper.clamp_double(sound.getPitch() * poolEntry.getPitch(), 0.5D, 2.0D);
			} catch (final Throwable t) {
				ModLog.error("getNormalizedPitch(): Unable to calculate " + soundName, t);
			}
		}
		return result;
	}

	public static void configureSound() {
		int totalChannels = -1;

		try {
			final boolean create = !AL.isCreated();
			if (create)
				AL.create();
			final IntBuffer ib = BufferUtils.createIntBuffer(1);
			ALC10.alcGetInteger(AL.getDevice(), ALC11.ALC_MONO_SOURCES, ib);
			totalChannels = ib.get(0);
			if (create)
				AL.destroy();
		} catch (final Throwable e) {
			e.printStackTrace();
		}

		normalChannelCount = ModOptions.normalSoundChannelCount;
		streamChannelCount = ModOptions.streamingSoundChannelCount;

		if (ModOptions.autoConfigureChannels && totalChannels > 64) {
			totalChannels = ((totalChannels + 1) * 3) / 4;
			streamChannelCount = totalChannels / 5;
			normalChannelCount = totalChannels - streamChannelCount;
		}

		ModLog.info("Sound channels: %d normal, %d streaming (total avail: %s)", normalChannelCount, streamChannelCount,
				totalChannels == -1 ? "UNKNOWN" : Integer.toString(totalChannels));
		SoundSystemConfig.setNumberNormalChannels(normalChannelCount);
		SoundSystemConfig.setNumberStreamingChannels(streamChannelCount);

	}

}
