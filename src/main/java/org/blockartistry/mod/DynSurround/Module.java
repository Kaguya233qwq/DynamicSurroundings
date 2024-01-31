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

package org.blockartistry.mod.DynSurround;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.blockartistry.mod.DynSurround.compat.ILOTRProxy;
import org.blockartistry.mod.DynSurround.compat.NoLotrProxy;
import org.blockartistry.mod.DynSurround.proxy.Proxy;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

@Mod(modid = Module.MOD_ID, useMetadata = true, dependencies = Module.DEPENDENCIES, version = Module.VERSION, guiFactory = Module.GUI_FACTORY)
public class Module {
	public static final String MOD_ID = "dsurround";
	public static final String MOD_NAME = "Dynamic Surroundings";
	public static final String VERSION = Tags.VERSION;
	public static final String DEPENDENCIES = "required-after:Forge@[10.13.4.1614,);required-after:gtnhmixins@[2.0.0,)";
	public static final String GUI_FACTORY = "org.blockartistry.mod.DynSurround.client.gui.ConfigGuiFactory";

    public static final String LOTR_PROXY_LOCATION = "org.blockartistry.mod.DynSurround.compat.LotrProxy";

    public static ILOTRProxy LOTR_PROXY;
    public static boolean LOTR;

	@Instance(MOD_ID)
	protected static Module instance;

	public static Module instance() {
		return instance;
	}

	@SidedProxy(clientSide = "org.blockartistry.mod.DynSurround.proxy.ProxyClient", serverSide = "org.blockartistry.mod.DynSurround.proxy.Proxy")
	protected static Proxy proxy;

	public static Proxy proxy() {
		return proxy;
	}

	protected static Configuration config;

	public static Configuration config() {
		return config;
	}

	protected static File dataDirectory;

	public static File dataDirectory() {
		return dataDirectory;
	}

	public Module() {
		ModLog.setLogger(LogManager.getLogger(MOD_ID));
	}

	@EventHandler
	public void preInit(final FMLPreInitializationEvent event) {

		FMLCommonHandler.instance().bus().register(this);

		// Load up our configuration
		dataDirectory = new File(event.getModConfigurationDirectory(), Module.MOD_ID);
		dataDirectory.mkdirs();
		config = new Configuration(new File(dataDirectory, Module.MOD_ID + ".cfg"));

		config.load();
		ModOptions.load(config);
		config.save();

		ModLog.DEBUGGING = ModOptions.enableDebugLogging;

		proxy.preInit(event);
	}

	@EventHandler
	public void init(final FMLInitializationEvent event) {
		proxy.init(event);
	}

	@EventHandler
	public void postInit(final FMLPostInitializationEvent event) {
        LOTR = Loader.isModLoaded("lotr");
        if (LOTR && !ModOptions.useNoLotrProxy) {
            try {
                LOTR_PROXY = Class.forName(LOTR_PROXY_LOCATION).asSubclass(ILOTRProxy.class).getDeclaredConstructor().newInstance();
            }
            catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                   InvocationTargetException ignored) {
            }
        }
        if (LOTR_PROXY == null) {
            LOTR_PROXY = new NoLotrProxy();
        }
        proxy.postInit(event);
		config.save();
	}

	@EventHandler
	public void serverStarting(final FMLServerStartingEvent event) {
		proxy.serverStarting(event);
	}

}
