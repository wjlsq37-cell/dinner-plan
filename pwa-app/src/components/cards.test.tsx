import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RecipeCard, RestaurantCard } from "./cards";
import { defaultState } from "../lib/store";

afterEach(cleanup);

describe("result card interactions", () => {
  it("opens from the card body and keyboard", () => {
    const open = vi.fn();
    render(<MemoryRouter><RecipeCard item={defaultState.recipeCache[0]} saved={false} toggle={vi.fn()} open={open}/></MemoryRouter>);
    fireEvent.click(screen.getByRole("heading", { name: defaultState.recipeCache[0].name }));
    expect(open).toHaveBeenCalledTimes(1);
    const card = screen.getByRole("link", { name: `查看菜谱：${defaultState.recipeCache[0].name}` });
    fireEvent.keyDown(card, { key: "Enter" });
    fireEvent.keyDown(card, { key: " " });
    expect(open).toHaveBeenCalledTimes(3);
  });

  it("keeps save independent from card navigation", () => {
    const open = vi.fn(); const toggle = vi.fn();
    render(<MemoryRouter><RecipeCard item={defaultState.recipeCache[0]} saved={false} toggle={toggle} open={open}/></MemoryRouter>);
    fireEvent.click(screen.getByRole("button", { name: "收藏" }));
    expect(toggle).toHaveBeenCalledWith({ kind: "recipe", id: defaultState.recipeCache[0].id });
    expect(open).not.toHaveBeenCalled();
  });

  it("omits the restaurant recommendation reason from result cards", () => {
    render(<MemoryRouter><RestaurantCard item={defaultState.restaurantCache[0]} saved={false} toggle={vi.fn()} open={vi.fn()}/></MemoryRouter>);
    expect(screen.queryByText(defaultState.restaurantCache[0].reason)).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: defaultState.restaurantCache[0].name })).toBeVisible();
  });
});
