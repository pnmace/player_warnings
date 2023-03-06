/*
 * Copyright (c) 2021, Parker Mace <https://github.com/pnmace>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.playerwarnings;

import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.util.EnumSet;
import java.util.List;

import static net.runelite.api.WorldType.*;

@PluginDescriptor(
        name = "Player Warnings",
        description = "Sends a warning if players appear on your screen in pvp situations",
        tags = {"pvp", "notifications", "warnings", "hcim", "uim"}
)

// add possible support for a minimum lvl warning when in deadman worlds/wilderness

public class PlayerWarningsPlugin extends Plugin
{
   static final String CONFIG_GROUP = "playerWarnings";

   @Inject
   private Client client;

   @Inject
   private PlayerWarningsConfig config;

   @Inject
   private Notifier notifier;

   @Provides
   PlayerWarningsConfig getConfig(ConfigManager configManager)
   {
      return configManager.getConfig(PlayerWarningsConfig.class);
   }

   private boolean inPvp;
   private boolean onlyAttackers;
   private WorldType currentType;
   private int warns = 0;

   @Subscribe
   public void onConfigChanged(ConfigChanged event)
   {
      if (!config.InPvp())
      {
         inPvp = false;
      }

      if (!config.onlyAttackers())
      {
         onlyAttackers = false;
      }
   }

   @Subscribe
   public void onGameTick(GameTick event)
   {

      if (config.InPvp())
      {
         EnumSet<WorldType> worldTypeEnumSet = client.getWorldType();

         for (WorldType i : worldTypeEnumSet)
         {
            if (i == DEADMAN || i == PVP || i == HIGH_RISK)
            {
               inPvp = true;
               currentType = i;
               break;
            }
            inPvp = false;
         }
      }

      if (client.getVarbitValue(5963) == 1 || inPvp == true)
      {
         // using the specorb varbit to check for safezone
         // this is the varbit for dmm safezone, but it appears to be set to 1 when on normal worlds
         // client.getVarbitValue(78) == 1)
         if (client.getVarbitValue(8121) == 0)
         {
            // reset the warn counter if we are in a pvp safezone
            warns = 0;
            return;
         }

         Player localPlayer = client.getLocalPlayer();
         List<Player> players = client.getPlayers();
         int combatlvl = client.getLocalPlayer().getCombatLevel();
         int maxlvl = combatlvl+getLevelDifference(client.getWorldType());
         int minlvl = combatlvl-getLevelDifference(client.getWorldType());

         // reset the warn counter if we are the only player on screen
         if (players.size() == 1) warns = 0;

         // iterate over the list of players to search for matches
         for (Player i : players)
         {
            // do not spam the screen forever if we end up getting attacked
            // this would make tanking/running very distracting
            if (warns > 4) break;
            if (i == localPlayer) continue;

            // check for attackers here
            if (config.onlyAttackers())
            {
               if (currentType == DEADMAN)
               {
                  notifier.notify(i.getName() + " is nearby!");
                  ++warns;
                  continue;
               }
               if (i.getCombatLevel() >= minlvl && i.getCombatLevel() <= maxlvl)
               {
                  notifier.notify(i.getName() + " combat level " + i.getCombatLevel() + " is nearby!");
                  ++warns;
               }
               continue;
            }
            notifier.notify(i.getName() + " combat level " + i.getCombatLevel() + " is nearby!");
            ++warns;
         }
      } else {
         // reset the warns variable if we leave the wilderness
         warns = 0;
      }
   }

   private int getLevelDifference(EnumSet<WorldType> worldTypeEnumSet)
   {
      int difference = 0;

      for (WorldType i : worldTypeEnumSet)
      {
         if (i == PVP || i == DEADMAN || i == HIGH_RISK)
         {
            difference += 15;
            break;
         }
      }

      if (client.getVarbitValue(5963) == 1)
      {
         difference += getWildyLvl();
      }

      return difference;
   }

   // change this eventually to be when within coords 2944 <= x < ? and 3520 <= z < ? because combat ranges are scuffed
   private int getWildyLvl()
   {
      String widgitInfo = client.getWidget(WidgetInfo.PVP_WILDERNESS_LEVEL).getText().replace("Level: ", "");

      return Integer.parseInt(widgitInfo);
   }
}
