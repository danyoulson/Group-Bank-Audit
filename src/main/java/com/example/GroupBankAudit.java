package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@PluginDescriptor(
	name = "Example"
)
public class GroupBankAudit extends Plugin
{
		@Inject
		private Client client;
		@Inject
		private GroupIronmenTrackerConfig config;
		@Inject
		private DataManager dataManager;
		@Inject
		private ItemManager itemManager;
		private int itemsDeposited = 0;
		private static final int SECONDS_BETWEEN_UPLOADS = 1;
		private static final int SECONDS_BETWEEN_INFREQUENT_DATA_CHANGES = 60;
		private static final int DEPOSIT_ITEM = 12582914;
		private static final int DEPOSIT_INVENTORY = 12582916;
		private static final int DEPOSIT_EQUIPMENT = 12582918;
		private static final int CHATBOX_ENTERED = 681;
		private static final int GROUP_STORAGE_LOADER = 293;
		public static List<ItemContainerItem> beforeItems = new ArrayList<>();
		public static List<ItemContainerItem> afterItems = new ArrayList<>();

		@Override
		protected void startUp() throws Exception {
			log.info("Group Ironmen Tracker started!");
		}

		@Override
		protected void shutDown() throws Exception {
			log.info("Group Ironmen Tracker stopped!");
		}

		@Schedule(
				period = SECONDS_BETWEEN_UPLOADS,
				unit = ChronoUnit.SECONDS,
				asynchronous = true
		)
		public void submitToApi() {
			dataManager.submitToApi();
		}

		@Schedule(
				period = SECONDS_BETWEEN_UPLOADS,
				unit = ChronoUnit.SECONDS
		)
		public void updateThingsThatDoChangeOften() {
			if (doNotUseThisData())
				return;
			Player player = client.getLocalPlayer();
			String playerName = player.getName();
		}

		@Schedule(
				period = SECONDS_BETWEEN_INFREQUENT_DATA_CHANGES,
				unit = ChronoUnit.SECONDS
		)
		public void updateThingsThatDoNotChangeOften() {
			if (doNotUseThisData())
				return;
			String playerName = client.getLocalPlayer().getName();
		}

		@Subscribe
		public void onGameTick(GameTick gameTick) throws InterruptedException {
			--itemsDeposited;
			Widget groupStorageLoaderText = client.getWidget(GROUP_STORAGE_LOADER, 1);
			if (groupStorageLoaderText != null) {
				if (groupStorageLoaderText.getText().equalsIgnoreCase("saving...")) {//need to do calc here, after
					//log.info("changes have been made");
					afterItems = dataManager.getSharedBank().mostRecentState().getItems();
					getDifference(beforeItems, afterItems);
					//TODO save all items in inventory at the end
				}
				else if(groupStorageLoaderText.getText().equalsIgnoreCase("loading group storage...")){//before
					//log.info("opening the group storage");
					beforeItems = dataManager.getSharedBank().mostRecentState().getItems();
					//TODO save all items in inventory at the start
				}
			}
		}

		@Subscribe
		public void onStatChanged(StatChanged statChanged) {
			if (doNotUseThisData())
				return;
			String playerName = client.getLocalPlayer().getName();
		}

		@Subscribe
		public void onItemContainerChanged(ItemContainerChanged event) {
			if (doNotUseThisData())
				return;
			String playerName = client.getLocalPlayer().getName();
			final int id = event.getContainerId();
			ItemContainer container = event.getItemContainer();

			if (id == InventoryID.GROUP_STORAGE.getId()) {
				//log.info("Group Bank accessed");
				dataManager.getSharedBank().update(new ItemContainerState(playerName, container, itemManager));

				ArrayList<Object> myItems = new ArrayList<>();
				myItems.add(dataManager.getSharedBank().mostRecentState().get());
				//log.info(myItems.toString());
			}
		}

		@Subscribe
		private void onScriptPostFired(ScriptPostFired event) {
			if (event.getScriptId() == CHATBOX_ENTERED && client.getWidget(WidgetInfo.DEPOSIT_BOX_INVENTORY_ITEMS_CONTAINER) != null) {
				itemsMayHaveBeenDeposited();
			}
		}

		@Subscribe
		private void onMenuOptionClicked(MenuOptionClicked event) {
			final int param1 = event.getParam1();
			final MenuAction menuAction = event.getMenuAction();
			if (menuAction == MenuAction.CC_OP) {
				if (param1 == DEPOSIT_ITEM || param1 == DEPOSIT_INVENTORY || param1 == DEPOSIT_EQUIPMENT) {
					itemsMayHaveBeenDeposited();
				}
			}
		}

		private void itemsMayHaveBeenDeposited() {
			// NOTE: In order to determine if an item has gone through the deposit box we first detect if any of the menu
			// actions were performed OR a custom amount was entered while the deposit box inventory widget was opened.
			// Then we allow up to two game ticks were an inventory changed event can occur and at that point we assume
			// it must have been caused by the action detected just before. We can't check the inventory at the time of
			// either interaction since the inventory may have not been updated yet. We also cannot just check that the deposit
			// box window is open in the item container event since it is possible for a player to close the widget before
			// the event handler is called.
			itemsDeposited = 2;
		}

		private void updateDeposited(ItemContainerState newState, ItemContainerState previousState) {
			ItemContainerState deposited = newState.whatGotRemoved(previousState);
			dataManager.getDeposited().update(deposited);
		}

		private boolean doNotUseThisData() {
			return client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null;
		}

		@Provides
		GroupIronmenTrackerConfig provideConfig(ConfigManager configManager) {
			return configManager.getConfig(GroupIronmenTrackerConfig.class);
		}

		private List<ItemContainerItem> getDifference(List<ItemContainerItem> beforeItems, List<ItemContainerItem> afterItems) {
			List<ItemContainerItem> itemDifference = new ArrayList<>();
			Map<Integer,Integer> beforeItemIds = new HashMap<>();
			for (ItemContainerItem items:beforeItems
				 ) {
				beforeItemIds.put(items.getId(), items.getQuantity());
			}
			Map<Integer,Integer> afterItemIds = new HashMap<>();
			for (ItemContainerItem items:afterItems
			) {
				afterItemIds.put(items.getId(),items.getQuantity());
			}
			log.info("Before Items:\n "+beforeItemIds.toString());
			log.info("After Items:\n "+afterItemIds.toString());
			return itemDifference;
		}
}