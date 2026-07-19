package dev.aitmore.light;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * A marker block welded directly above every TARDIS exterior (see TravelHandlerMixin for how
 * it gets placed, and ExteriorLightBlockEntity for how it removes itself once the exterior
 * below it is gone). Modeled on vanilla's own {@code minecraft:light}: no collision, no
 * outline, not selectable/breakable by players - it only exists to carry a light level.
 *
 * Light itself has no color channel in vanilla Minecraft, so "goes red when alarms are on"
 * can't be real colored illumination - instead ALARM flips the render type from INVISIBLE to
 * MODEL, swapping in a small red-tinted model (see assets/.../blockstates/exterior_light.json)
 * purely as a visible indicator; the LEVEL property (the actual light emission) still only
 * ever varies in brightness, driven by how "materialized" the exterior currently is.
 */
public class ExteriorLightBlock extends Block implements BlockEntityProvider {

    public static final IntProperty LEVEL = Properties.LEVEL_15;
    public static final BooleanProperty ALARM = BooleanProperty.of("alarm");

    public ExteriorLightBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getStateManager().getDefaultState().with(LEVEL, 0).with(ALARM, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LEVEL, ALARM);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return state.get(ALARM) ? BlockRenderType.MODEL : BlockRenderType.INVISIBLE;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    @Override
    public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ExteriorLightBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient() ? null : (world1, pos, state1, entity) -> {
            if (entity instanceof ExteriorLightBlockEntity light)
                light.serverTick((net.minecraft.server.world.ServerWorld) world1, pos);
        };
    }
}
