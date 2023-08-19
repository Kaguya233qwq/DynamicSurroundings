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

package org.blockartistry.mod.DynSurround.client;

import java.util.HashSet;
import java.util.Set;

import org.blockartistry.mod.DynSurround.ModLog;
import org.blockartistry.mod.DynSurround.ModOptions;
import org.blockartistry.mod.DynSurround.client.EnvironStateHandler.EnvironState;
import org.blockartistry.mod.DynSurround.data.SoundRegistry;
import org.blockartistry.mod.DynSurround.event.SoundConfigEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gnu.trove.map.hash.TObjectIntHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;

@SideOnly(Side.CLIENT)
public class SoundBlockHandler implements IClientEffectHandler {

	private final Set<String> soundsToBlock = new HashSet<>();
	private final TObjectIntHashMap<String> soundCull = new TObjectIntHashMap<>();

	public SoundBlockHandler() {
	}

	@Override
	public void process(final World world, final EntityPlayer player) {

	}

	@Override
	public boolean hasEvents() {
		return true;
	}

	@SubscribeEvent
	public void soundConfigReload(final SoundConfigEvent.Reload event) {
		this.soundsToBlock.clear();
		this.soundCull.clear();
		final SoundHandler handler = Minecraft.getMinecraft().getSoundHandler();
		for (final Object resource : handler.sndRegistry.getKeys()) {
			final String rs = resource.toString();
			if (SoundRegistry.isSoundBlocked(rs)) {
				ModLog.debug("Blocking sound '%s'", rs);
				this.soundsToBlock.add(rs);
			} else if (SoundRegistry.isSoundCulled(rs)) {
				ModLog.debug("Culling sound '%s'", rs);
				this.soundCull.put(rs, -ModOptions.soundCullingThreshold);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void soundEvent(final PlaySoundEvent17 event) {
		if (event.sound == null || event.sound.getPositionedSoundLocation() == null)
			return;

		final String resource = event.sound.getPositionedSoundLocation().toString();
		if (this.soundsToBlock.contains(resource)) {
			event.result = null;
			return;
		}

		if (ModOptions.soundCullingThreshold <= 0)
			return;

		// Get the last time the sound was seen
		final int lastOccurance = this.soundCull.get(resource);
		if (lastOccurance == 0)
			return;

		final int currentTick = EnvironState.getTickCounter();
		if ((currentTick - lastOccurance) < ModOptions.soundCullingThreshold) {
			event.result = null;
		} else {
			this.soundCull.put(resource, currentTick);
		}
	}
}
