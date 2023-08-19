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

import java.util.ArrayList;
import java.util.List;

import org.blockartistry.mod.DynSurround.ModOptions;
import org.blockartistry.mod.DynSurround.client.EnvironStateHandler.EnvironState;
import org.blockartistry.mod.DynSurround.client.sound.SoundEffect;
import org.blockartistry.mod.DynSurround.client.sound.SoundManager;
import org.blockartistry.mod.DynSurround.client.weather.Weather;
import org.blockartistry.mod.DynSurround.compat.BlockPos;
import org.blockartistry.mod.DynSurround.data.BiomeRegistry;
import org.blockartistry.mod.DynSurround.event.DiagnosticEvent;
import org.blockartistry.mod.DynSurround.event.RegistryReloadEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gnu.trove.map.hash.TObjectIntHashMap;
import net.minecraft.block.Block;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.particle.EntityDropParticleFX;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;
import net.minecraftforge.event.entity.EntityEvent.EntityConstructing;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

@SideOnly(Side.CLIENT)
public class PlayerSoundEffectHandler implements IClientEffectHandler {

	private static final List<EntityDropParticleFX> drops = new ArrayList<>();

	private static boolean doBiomeSounds() {
		return EnvironState.isPlayerUnderground() || !EnvironState.isPlayerInside();
	}

	private static List<SoundEffect> getBiomeSounds(final String conditions) {
		// Need to collect sounds from all the applicable biomes
		// along with their weights.
		final TObjectIntHashMap<SoundEffect> sounds = new TObjectIntHashMap<>();
		final TObjectIntHashMap<BiomeGenBase> weights = BiomeSurveyHandler.getBiomes();
		for (final BiomeGenBase biome : weights.keySet()) {
			final List<SoundEffect> bs = BiomeRegistry.getSounds(biome, conditions);
			for (final SoundEffect sound : bs)
				sounds.put(sound, sounds.get(sound) + weights.get(biome));
		}

		// Scale the volumes in the resulting list based on the weights
		final List<SoundEffect> result = new ArrayList<>();
		final int area = BiomeSurveyHandler.getArea();
		for (final SoundEffect sound : sounds.keySet()) {
			final float scale = 0.3F + 0.7F * ((float) sounds.get(sound) / (float) area);
			result.add(SoundEffect.scaleVolume(sound, scale));
		}

		return result;
	}

	private static void resetSounds() {
		SoundManager.clearSounds();
		drops.clear();
	}

	@Override
	public void process(final World world, final EntityPlayer player) {

		// Dead players hear no sounds
		if (player.isDead) {
			resetSounds();
			return;
		}

		final BiomeGenBase playerBiome = EnvironState.getPlayerBiome();
		final String conditions = EnvironState.getConditions();

		final List<SoundEffect> sounds = new ArrayList<>();
		if (doBiomeSounds())
			sounds.addAll(getBiomeSounds(conditions));
		sounds.addAll(BiomeRegistry.getSounds(BiomeRegistry.PLAYER, conditions));

		SoundManager.queueAmbientSounds(sounds);

		if (doBiomeSounds()) {
			final SoundEffect sound = BiomeRegistry.getSpotSound(playerBiome, conditions, EnvironState.RANDOM);
			if (sound != null)
				SoundManager.playSoundAtPlayer(player, sound);
		}

		final SoundEffect sound = BiomeRegistry.getSpotSound(BiomeRegistry.PLAYER, conditions, EnvironState.RANDOM);
		if (sound != null)
			SoundManager.playSoundAtPlayer(player, sound);

		processWaterDrops();

		SoundManager.update();
	}

	@Override
	public boolean hasEvents() {
		return true;
	}

	/*
	 * Fired when the underlying biome config is reloaded.
	 */
	@SubscribeEvent
	public void registryReloadEvent(final RegistryReloadEvent.Biome event) {
		resetSounds();
	}

	/*
	 * Fired when the player joins a world, such as when the dimension changes.
	 */
	@SubscribeEvent
	public void playerJoinWorldEvent(final EntityJoinWorldEvent event) {
		if (event.entity.worldObj.isRemote && EnvironState.isPlayer(event.entity))
			resetSounds();
	}

	@SubscribeEvent
	public void diagnostics(final DiagnosticEvent.Gather event) {
        String builder = "SoundSystem: " + SoundManager.currentSoundCount() + '/' +
            SoundManager.maxSoundCount();
		event.output.add(builder);
        event.output.addAll(SoundManager.getSounds());
	}

	@SubscribeEvent
	public void entityCreateEvent(final EntityConstructing event) {
		if (event.entity instanceof EntityDropParticleFX) {
			drops.add((EntityDropParticleFX) event.entity);
		}
	}

	private void processWaterDrops() {
		if (drops.isEmpty())
			return;

		final World world = EnvironState.getWorld();
		for (final EntityDropParticleFX drop : drops) {
			if (drop.isEntityAlive()) {
				if (drop.posY < 1)
					continue;
				final int x = MathHelper.floor_double(drop.posX);
				final int y = MathHelper.floor_double(drop.posY + 0.3D);
				final int z = MathHelper.floor_double(drop.posZ);
				Block block = world.getBlock(x, y, z);
				if (block != Blocks.air && !block.isLeaves(world, x, y, z)) {
					int soundY = y - 1;
					for (; soundY > 0 && (block = world.getBlock(x, soundY, z)) == Blocks.air; soundY--)
						;
					if (soundY > 0 && block.getMaterial().isSolid()) {
						final int distance = y - soundY;
						SoundManager.playSoundAt(new BlockPos(x, soundY + 1, z), BiomeRegistry.WATER_DRIP,
								40 + distance * 2);
					}
				}
			}
		}

		drops.clear();
	}

	/*
	 * Determines if the sound needs to be replaced by the event handler.
	 */
	private static boolean replaceRainSound(final String name) {
		return "ambient.weather.rain".equals(name);
	}

	/*
	 * Intercept the sound events and patch up the rain sound. If the rain
	 * experience is to be Vanilla let it just roll on through.
	 */
	@SubscribeEvent
	public void soundEvent(final PlaySoundEvent17 event) {
		if (event.sound == null)
			return;

		if ((ModOptions.alwaysOverrideSound || !Weather.doVanilla()) && replaceRainSound(event.name)) {
			final ISound sound = event.sound;
			event.result = new PositionedSoundRecord(Weather.getCurrentStormSound(),
					Weather.getCurrentVolume(), sound.getPitch(), sound.getXPosF(), sound.getYPosF(),
					sound.getZPosF());
		}
	}

}
