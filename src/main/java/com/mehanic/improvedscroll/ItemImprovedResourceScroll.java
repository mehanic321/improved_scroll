package com.mehanic.improvedscroll;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.core.Network;
import com.minecolonies.core.client.gui.WindowResourceList;
import com.minecolonies.core.colony.buildings.modules.BuildingResourcesModule;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import com.minecolonies.core.network.messages.server.ResourceScrollSaveWarehouseSnapshotMessage;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.minecolonies.core.tileentities.TileEntityWareHouse;
import com.minecolonies.core.items.AbstractItemMinecolonies;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.minecolonies.api.util.constant.Constants.STACKSIZE;
import static com.minecolonies.api.util.constant.NbtTagConstants.*;
import static com.minecolonies.api.util.constant.TranslationConstants.*;

public class ItemImprovedResourceScroll extends AbstractItemMinecolonies
{
    public ItemImprovedResourceScroll(final Item.Properties properties)
    {
        super("improved_resource_scroll", properties.stacksTo(STACKSIZE));
    }

    private static void openWindow(final CompoundTag compound, final Player player)
    {
        final int colonyId = compound.getInt(TAG_COLONY_ID);
        final BlockPos builderPos = compound.contains(TAG_BUILDER) ? BlockPosUtil.read(compound, TAG_BUILDER) : null;

        final IColonyView colonyView = IColonyManager.getInstance().getColonyView(colonyId, Minecraft.getInstance().level.dimension());
        if (colonyView != null)
        {
            final IBuildingView buildingView = colonyView.getClientBuildingManager().getBuilding(builderPos);
            if (buildingView instanceof BuildingBuilder.View builderBuildingView)
            {
                final String currentHash = getWorkOrderHash(buildingView);
                final String storedHash = compound.contains(TAG_WAREHOUSE_SNAPSHOT_WO_HASH) ? compound.getString(TAG_WAREHOUSE_SNAPSHOT_WO_HASH) : null;
                final boolean snapshotNeedsUpdate = !Objects.equals(currentHash, storedHash);

                Map<String, Integer> warehouseSnapshot = new HashMap<>();
                if (snapshotNeedsUpdate)
                {
                    Network.getNetwork().sendToServer(new ResourceScrollSaveWarehouseSnapshotMessage(builderPos));
                }
                else
                {
                    if (compound.contains(TAG_WAREHOUSE_SNAPSHOT))
                    {
                        final CompoundTag warehouseSnapshotCompound = compound.getCompound(TAG_WAREHOUSE_SNAPSHOT);
                        warehouseSnapshot = warehouseSnapshotCompound.getAllKeys().stream()
                                              .collect(Collectors.toMap(k -> k, warehouseSnapshotCompound::getInt));
                    }
                }

                new WindowResourceList(builderBuildingView, warehouseSnapshot).open();
            }
            else
            {
                MessageUtils.format(Component.translatable(TranslationConstants.COM_MINECOLONIES_SCROLL_NO_COLONY)).sendTo(player);
            }
        }
        else
        {
            MessageUtils.format(Component.translatable(TranslationConstants.COM_MINECOLONIES_SCROLL_NO_COLONY)).sendTo(player);
        }
    }

    @NotNull
    private static String getWorkOrderHash(final IBuildingView buildingView)
    {
        final Optional<IWorkOrderView> currentWorkOrder = buildingView.getColony()
                                                            .getWorkOrders()
                                                            .stream()
                                                            .filter(o -> o.getClaimedBy().equals(buildingView.getID()))
                                                            .max(Comparator.comparingInt(IWorkOrderView::getPriority));
        if (currentWorkOrder.isEmpty())
        {
            return "";
        }

        long location = currentWorkOrder.get().getLocation().asLong();
        return location + "__" + currentWorkOrder.get().getStructurePack();
    }

    private static void updateWarehouseSnapshot(final BlockPos warehousePos, final CompoundTag compound, final Player player)
    {
        if (!compound.contains(TAG_COLONY_ID) || !compound.contains(TAG_BUILDER))
        {
            MessageUtils.format(Component.translatable(TranslationConstants.COM_MINECOLONIES_SCROLL_NO_COLONY)).sendTo(player);
            return;
        }

        final IColonyView colonyView = IColonyManager.getInstance().getColonyView(compound.getInt(TAG_COLONY_ID), Minecraft.getInstance().level.dimension());
        if (colonyView != null)
        {
            final BlockPos builderPos = BlockPosUtil.read(compound, TAG_BUILDER);
            final IBuildingView buildingView = colonyView.getClientBuildingManager().getBuilding(builderPos);
            if (buildingView instanceof BuildingBuilder.View)
            {
                final String currentHash = getWorkOrderHash(buildingView);
                final WarehouseSnapshot warehouseSnapshotData = gatherWarehouseSnapshot(buildingView, warehousePos, currentHash, player);

                if (warehouseSnapshotData != null)
                {
                    Network.getNetwork().sendToServer(new ResourceScrollSaveWarehouseSnapshotMessage(builderPos, warehouseSnapshotData.snapshot, warehouseSnapshotData.hash));
                }
                else
                {
                    Network.getNetwork().sendToServer(new ResourceScrollSaveWarehouseSnapshotMessage(builderPos));
                }
            }
        }
    }

    @Nullable
    private static ItemImprovedResourceScroll.WarehouseSnapshot gatherWarehouseSnapshot(
      final IBuildingView buildingView,
      final BlockPos warehouseBlockPos,
      final String hash,
      final Player player)
    {
        final IBuildingView warehouse = buildingView.getColony().getClientBuildingManager().getBuilding(warehouseBlockPos);

        if (warehouse == null)
        {
            MessageUtils.format(Component.translatable(TranslationConstants.COM_MINECOLONIES_SCROLL_WRONG_COLONY)).sendTo(player);
            return null;
        }

        if (hash.isBlank())
        {
            return null;
        }

        final BuildingResourcesModuleView resourcesModuleView = buildingView.getModuleViewByType(BuildingResourcesModuleView.class);

        final Map<String, Integer> items = new HashMap<>();
        for (final BlockPos container : warehouse.getContainerList())
        {
            final BlockEntity blockEntity = warehouse.getColony().getWorld().getBlockEntity(container);
            if (blockEntity instanceof TileEntityRack rack)
            {
                rack.getAllContent().forEach((item, amount) -> {
                    final int hashCode = item.getItemStack().hasTag() ? item.getItemStack().getTag().hashCode() : 0;
                    final String key = item.getItemStack().getDescriptionId() + "-" + hashCode;
                    if (!resourcesModuleView.getResources().containsKey(key))
                    {
                        return;
                    }

                    int oldAmount = items.getOrDefault(key, 0);
                    items.put(key, oldAmount + amount);
                });
            }
        }

        return new WarehouseSnapshot(items, hash);
    }

    @Override
    @NotNull
    public InteractionResult useOn(UseOnContext ctx)
    {
        final ItemStack scroll = ctx.getPlayer().getItemInHand(ctx.getHand());

        final CompoundTag compound = scroll.getOrCreateTag();
        final BlockEntity entity = ctx.getLevel().getBlockEntity(ctx.getClickedPos());

        if (ctx.getLevel().isClientSide)
        {
            if (entity instanceof AbstractTileEntityColonyBuilding buildingEntity)
            {
                if (buildingEntity instanceof TileEntityWareHouse)
                {
                    updateWarehouseSnapshot(buildingEntity.getTilePos(), compound, ctx.getPlayer());
                }
            }
            else
            {
                if (!ctx.getPlayer().isShiftKeyDown())
                {
                    openWindow(compound, ctx.getPlayer());
                }
            }
        }
        else if (entity instanceof AbstractTileEntityColonyBuilding buildingEntity)
        {
            if (buildingEntity.getBuilding() instanceof BuildingBuilder)
            {
                compound.putInt(TAG_COLONY_ID, buildingEntity.getColonyId());
                BlockPosUtil.write(compound, TAG_BUILDER, buildingEntity.getPosition());

                MessageUtils.format(Component.translatable(TranslationConstants.COM_MINECOLONIES_SCROLL_BUILDING_SET, buildingEntity.getColony().getName())).sendTo(ctx.getPlayer());
            }
            else if (buildingEntity.getBuilding() instanceof BuildingWareHouse)
            {
                MessageUtils.format(Component.translatable(TranslationConstants.COM_MINECOLONIES_SCROLL_SNAPSHOT)).sendTo(ctx.getPlayer());
            }
            else if (buildingEntity.getBuilding() != null)
            {
                final MutableComponent buildingTypeComponent = MessageUtils.format(buildingEntity.getBuilding().getBuildingType().getTranslationKey()).create();
                MessageUtils.format(Component.translatable(TranslationConstants.COM_MINECOLONIES_SCROLL_WRONG_BUILDING, buildingTypeComponent, buildingEntity.getColony().getName())).sendTo(ctx.getPlayer());
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    @NotNull
    public InteractionResultHolder<ItemStack> use(
      final Level worldIn,
      final Player playerIn,
      final InteractionHand hand)
    {
        final ItemStack clipboard = playerIn.getItemInHand(hand);

        if (worldIn.isClientSide)
        {
            if (!playerIn.isShiftKeyDown())
            {
                openWindow(clipboard.getOrCreateTag(), playerIn);
            }
            return new InteractionResultHolder<>(InteractionResult.SUCCESS, clipboard);
        }

        if (playerIn.isShiftKeyDown())
        {
            final CompoundTag compound = clipboard.getOrCreateTag();
            if (compound.contains(TAG_COLONY_ID) && compound.contains(TAG_BUILDER))
            {
                final int colonyId = compound.getInt(TAG_COLONY_ID);
                final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, worldIn.dimension());
                
                if (colony == null)
                {
                    MessageUtils.format(Component.translatable("improved_scroll.message.colony_not_found")).sendTo(playerIn);
                    return new InteractionResultHolder<>(InteractionResult.FAIL, clipboard);
                }

                if (!colony.getPermissions().hasPermission(playerIn, Action.ACCESS_HUTS))
                {
                    MessageUtils.format(Component.translatable("improved_scroll.message.no_permission")).sendTo(playerIn);
                    return new InteractionResultHolder<>(InteractionResult.FAIL, clipboard);
                }

                // 1. Find ANY Building at player position
                IBuilding building = null;
                for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
                    net.minecraft.util.Tuple<BlockPos, BlockPos> corners = b.getCorners();
                    BlockPos a = corners.getA();
                    BlockPos bPos = corners.getB();
                    BlockPos p = playerIn.blockPosition();
                    
                    if (p.getX() >= Math.min(a.getX(), bPos.getX()) && p.getX() <= Math.max(a.getX(), bPos.getX()) &&
                        p.getY() >= Math.min(a.getY(), bPos.getY()) && p.getY() <= Math.max(a.getY(), bPos.getY()) &&
                        p.getZ() >= Math.min(a.getZ(), bPos.getZ()) && p.getZ() <= Math.max(a.getZ(), bPos.getZ())) {
                        building = b;
                        break;
                    }
                }

                if (building != null)
                {
                    final BlockPos builderPos = BlockPosUtil.read(compound, TAG_BUILDER);
                    final IBuilding builder = colony.getServerBuildingManager().getBuilding(builderPos);
                    if (builder instanceof BuildingBuilder)
                    {
                        final BuildingResourcesModule module = builder.getFirstModuleOccurance(BuildingResourcesModule.class);
                        
                        if (module == null || module.getNeededResources().isEmpty())
                        {
                            MessageUtils.format(Component.translatable("improved_scroll.message.builder_no_requests")).sendTo(playerIn);
                            return new InteractionResultHolder<>(InteractionResult.SUCCESS, clipboard);
                        }

                        int retrievedCount = 0;
                        boolean inventoryFull = false;

                        for (final BuildingBuilderResource resource : module.getNeededResources().values())
                        {
                            int needed = resource.getAmount() - resource.getAvailable();
                            if (needed <= 0) continue;

                            final int inPlayerInv = InventoryUtils.getItemCountInItemHandler(new PlayerInvWrapper(playerIn.getInventory()),
                              stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, resource.getItemStack(), true, true));

                            needed -= inPlayerInv;
                            if (needed <= 0) continue;

                            // Iterate ALL containers in the found building
                            for (BlockPos containerPos : building.getContainers())
                            {
                                if (needed <= 0) break;
                                
                                BlockEntity te = colony.getWorld().getBlockEntity(containerPos);
                                if (te != null)
                                {
                                    AtomicInteger extractedFromContainer = new AtomicInteger(0);
                                    int finalNeeded = needed;
                                    
                                    te.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                                        for (int i = 0; i < handler.getSlots(); i++)
                                        {
                                            if (finalNeeded - extractedFromContainer.get() <= 0) break;

                                            ItemStack inSlot = handler.getStackInSlot(i);
                                            if (ItemStackUtils.compareItemStacksIgnoreStackSize(inSlot, resource.getItemStack(), true, true))
                                            {
                                                int toExtract = Math.min(inSlot.getCount(), finalNeeded - extractedFromContainer.get());
                                                
                                                ItemStack simulated = handler.extractItem(i, toExtract, true);
                                                if (!simulated.isEmpty())
                                                {
                                                    ItemStack remainder = ItemHandlerHelper.insertItemStacked(new PlayerInvWrapper(playerIn.getInventory()), simulated, true);
                                                    int canInsert = simulated.getCount() - remainder.getCount();
                                                    
                                                    if (canInsert > 0)
                                                    {
                                                        ItemStack extracted = handler.extractItem(i, canInsert, false);
                                                        if (!extracted.isEmpty())
                                                        {
                                                            ItemHandlerHelper.giveItemToPlayer(playerIn, extracted);
                                                            extractedFromContainer.addAndGet(extracted.getCount());
                                                        }
                                                    }
                                                    else
                                                    {
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    });
                                    
                                    int extracted = extractedFromContainer.get();
                                    needed -= extracted;
                                    retrievedCount += extracted;
                                }
                            }
                            
                            if (needed > 0 && retrievedCount > 0) {
                                 if (InventoryUtils.isItemHandlerFull(new PlayerInvWrapper(playerIn.getInventory()))) {
                                     inventoryFull = true;
                                 }
                            }
                        }
                        
                        if (retrievedCount > 0)
                        {
                            MessageUtils.format(Component.translatable("improved_scroll.message.retrieved", retrievedCount)).sendTo(playerIn);
                        }
                        else if (inventoryFull)
                        {
                            MessageUtils.format(Component.translatable("improved_scroll.message.inventory_full")).sendTo(playerIn);
                        }
                        else
                        {
                            MessageUtils.format(Component.translatable("improved_scroll.message.none_found")).sendTo(playerIn);
                        }
                    }
                }
                else
                {
                     MessageUtils.format(Component.translatable("improved_scroll.message.not_in_warehouse")).sendTo(playerIn);
                }
            }
        }

        return new InteractionResultHolder<>(InteractionResult.SUCCESS, clipboard);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable Level worldIn, List<Component> tooltip, TooltipFlag flagIn)
    {
        super.appendHoverText(stack, worldIn, tooltip, flagIn);

        if (worldIn == null)
        {
            return;
        }

        final CompoundTag compound = stack.getOrCreateTag();
        final int colonyId = compound.getInt(TAG_COLONY_ID);
        final BlockPos builderPos = BlockPosUtil.read(compound, TAG_BUILDER);

        final IColonyView colonyView = IColonyManager.getInstance().getColonyView(colonyId, worldIn.dimension());
        if (colonyView != null)
        {
            final IBuildingView buildingView = colonyView.getClientBuildingManager().getBuilding(builderPos);
            if (buildingView instanceof BuildingBuilder.View builderBuildingView)
            {
                String name = builderBuildingView.getWorkerName();
                tooltip.add(name != null && !name.trim().isEmpty()
                              ? Component.literal(ChatFormatting.DARK_PURPLE + name)
                              : Component.translatable(TranslationConstants.COM_MINECOLONIES_SCROLL_BUILDING_NO_WORKER));
            }
        }
    }

    private record WarehouseSnapshot(Map<String, Integer> snapshot, String hash) {}
}
