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

package org.blockartistry.mod.DynSurround.client.footsteps;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Scanner;

import org.blockartistry.mod.DynSurround.ModLog;
import org.blockartistry.mod.DynSurround.ModOptions;
import org.blockartistry.mod.DynSurround.client.IClientEffectHandler;
import org.blockartistry.mod.DynSurround.client.footsteps.game.system.ForgeDictionary;
import org.blockartistry.mod.DynSurround.client.footsteps.game.system.PFIsolator;
import org.blockartistry.mod.DynSurround.client.footsteps.game.system.PFReaderH;
import org.blockartistry.mod.DynSurround.client.footsteps.game.system.PFResourcePackDealer;
import org.blockartistry.mod.DynSurround.client.footsteps.game.system.PFSolver;
import org.blockartistry.mod.DynSurround.client.footsteps.game.system.UserConfigSoundPlayerWrapper;
import org.blockartistry.mod.DynSurround.client.footsteps.mcpackage.implem.AcousticsManager;
import org.blockartistry.mod.DynSurround.client.footsteps.mcpackage.implem.BasicPrimitiveMap;
import org.blockartistry.mod.DynSurround.client.footsteps.mcpackage.implem.LegacyCapableBlockMap;
import org.blockartistry.mod.DynSurround.client.footsteps.mcpackage.implem.NormalVariator;
import org.blockartistry.mod.DynSurround.client.footsteps.mcpackage.interfaces.IBlockMap;
import org.blockartistry.mod.DynSurround.client.footsteps.mcpackage.interfaces.IPrimitiveMap;
import org.blockartistry.mod.DynSurround.client.footsteps.mcpackage.interfaces.IVariator;
import org.blockartistry.mod.DynSurround.client.footsteps.parsers.AcousticsJsonReader;
import org.blockartistry.mod.DynSurround.client.footsteps.parsers.Register;
import org.blockartistry.mod.DynSurround.client.footsteps.util.property.simple.ConfigProperty;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

@SideOnly(Side.CLIENT)
public class Footsteps implements IResourceManagerReloadListener, IClientEffectHandler {

	public static Footsteps INSTANCE = null;

	// System
	private final PFResourcePackDealer dealer = new PFResourcePackDealer();
	private PFIsolator isolator;
	private boolean isFirstTime = true;

	public Footsteps() {
		INSTANCE = this;
		this.isolator = new PFIsolator();
	}

	public void reloadEverything() {
		this.isolator = new PFIsolator();

		final List<IResourcePack> repo = this.dealer.findResourcePacks();

		reloadBlockMap(repo);
		reloadPrimitiveMap(repo);
		reloadAcoustics(repo);
		this.isolator.setSolver(new PFSolver(this.isolator));
		reloadVariator(repo);

		this.isolator.setGenerator(new PFReaderH(this.isolator));
		/*
		 * this.isolator.setGenerator(getConfig().getInteger("custom.stance") == 0 ? new
		 * PFReaderH(this.isolator) : new PFReaderQP(this.isolator));
		 */
	}

	private void reloadVariator(final List<IResourcePack> repo) {
		final IVariator var = new NormalVariator();

		for (final IResourcePack pack : repo) {
			InputStream stream = null;
			try {
				stream = this.dealer.openVariator(pack);
				if (stream != null)
					var.loadConfig(ConfigProperty.fromStream(stream));
			} catch (final Exception e) {
				ModLog.debug("Unable to load variator data from pack %s", pack.getPackName());
			} finally {
				if (stream != null)
					try {
						stream.close();
					} catch (final IOException ignored) {
					}
			}
		}

		this.isolator.setVariator(var);
	}

	private void reloadBlockMap(final List<IResourcePack> repo) {
		final IBlockMap blockMap = new LegacyCapableBlockMap();

		ForgeDictionary.initialize(blockMap);

		for (final IResourcePack pack : repo) {
			InputStream stream = null;
			try {
				stream = this.dealer.openBlockMap(pack);
				if (stream != null)
					Register.setup(ConfigProperty.fromStream(stream), blockMap);
			} catch (final IOException e) {
				ModLog.debug("Unable to load block map data from pack %s", pack.getPackName());
			} finally {
				if (stream != null)
					try {
						stream.close();
					} catch (final IOException ignored) {
					}
			}
		}

		this.isolator.setBlockMap(blockMap);
	}

	private void reloadPrimitiveMap(final List<IResourcePack> repo) {
		final IPrimitiveMap primitiveMap = new BasicPrimitiveMap();

		for (final IResourcePack pack : repo) {
			InputStream stream = null;
			try {
				stream = this.dealer.openPrimitiveMap(pack);
				if (stream != null)
					Register.setup(ConfigProperty.fromStream(stream), primitiveMap);
			} catch (final IOException e) {
				ModLog.debug("Unable to load primitive map data from pack %s", pack.getPackName());
			} finally {
				if (stream != null)
					try {
						stream.close();
					} catch (final IOException ignored) {
					}
			}
		}

		this.isolator.setPrimitiveMap(primitiveMap);
	}

	private void reloadAcoustics(final List<IResourcePack> repo) {
		final AcousticsManager acoustics = new AcousticsManager(this.isolator);
		Scanner scanner = null;
		InputStream stream = null;

		for (final IResourcePack pack : repo) {
			try {
				stream = this.dealer.openAcoustics(pack);
				if (stream != null) {
					scanner = new Scanner(stream);
					final String jasonString = scanner.useDelimiter("\\Z").next();

					new AcousticsJsonReader("").parseJSON(jasonString, acoustics);
				}
			} catch (final IOException e) {
				ModLog.debug("Unable to load acoustic data from pack %s", pack.getPackName());
			} finally {
				try {
					if (scanner != null)
						scanner.close();
					if (stream != null)
						stream.close();
				} catch (final IOException ignored) {
				}
			}
		}

		this.isolator.setAcoustics(acoustics);
		this.isolator.setSoundPlayer(new UserConfigSoundPlayerWrapper(acoustics));
		this.isolator.setDefaultStepPlayer(acoustics);
	}

	@Override
	public void onResourceManagerReload(final IResourceManager var1) {
		ModLog.info("Resource Pack reload detected...");
		reloadEverything();
	}

	@Override
	public void process(World world, EntityPlayer player) {
		if (this.isFirstTime) {
			this.isFirstTime = false;
			reloadEverything();
		}
		this.isolator.onFrame();
		if (ModOptions.footstepsSoundFactor > 0)
			player.nextStepDistance = Integer.MAX_VALUE;
		else if (player.nextStepDistance == Integer.MAX_VALUE)
			player.nextStepDistance = 0;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onWorldUnload(final WorldEvent.Unload event) {
		if (event.world.isRemote && event.world.provider.dimensionId == 0) {
			this.isFirstTime = true;
		}
	}

	@Override
	public boolean hasEvents() {
		return true;
	}

	public IBlockMap getBlockMap() {
		return this.isolator.getBlockMap();
	}
}
