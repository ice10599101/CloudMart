import React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  getSoftwareHome: vi.fn(),
  listSoftware: vi.fn(),
  listSoftwareTopics: vi.fn(),
  listSoftwareCategories: vi.fn(),
  push: vi.fn(),
}));

vi.mock("umi", () => ({ history: { push: mocks.push } }));
vi.mock("@/api/software", () => ({
  getSoftwareHome: mocks.getSoftwareHome,
  listSoftware: mocks.listSoftware,
  listSoftwareTopics: mocks.listSoftwareTopics,
  listSoftwareCategories: mocks.listSoftwareCategories,
}));
vi.mock("@/components/software/SoftwareCardGrid", () => ({
  default: ({ cards }: { cards: Array<{ id: string; name: string }> }) => (
    <div>{cards.map((card) => <span key={card.id}>{card.name}</span>)}</div>
  ),
}));
vi.mock("@ant-design/icons", () => {
  const Icon = () => null;
  return {
    CloudUploadOutlined: Icon,
    FolderOpenOutlined: Icon,
    ReloadOutlined: Icon,
    RocketOutlined: Icon,
    SearchOutlined: Icon,
    TrophyOutlined: Icon,
  };
});
vi.mock("antd", () => {
  const Box = ({ children }: React.PropsWithChildren) => <div>{children}</div>;
  const Input = ({ value, onChange }: any) => (
    <input value={value} onChange={onChange} />
  );
  return {
    Button: ({ children, onClick }: any) => <button onClick={onClick}>{children}</button>,
    Empty: ({ description, children }: any) => <div>{description}{children}</div>,
    Input,
    Spin: Box,
    Tag: Box,
    Typography: { Text: Box },
  };
});

import SoftwareHome from "./SoftwareHome";

describe("SoftwareHome recommendations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    const card = { id: "17", name: "推荐软件", favoriteCount: 0, commentCount: 0 };
    mocks.getSoftwareHome.mockResolvedValue({ data: { data: [] } });
    mocks.listSoftwareTopics.mockResolvedValue({ data: { data: [] } });
    mocks.listSoftwareCategories.mockResolvedValue({ data: { success: true, data: [] } });
    mocks.listSoftware.mockImplementation(async () => ({
      data: {
        success: true,
        data: { records: [card], total: 1 },
      },
    }));
  });

  afterEach(cleanup);

  it("uses the same recommended ordering on the home rail and its all-results link", async () => {
    render(<SoftwareHome />);

    await waitFor(() => expect(mocks.listSoftware).toHaveBeenCalledWith({
      sort: "recommend",
      page: 1,
      size: 8,
    }));
    await waitFor(() => expect(screen.getAllByText("推荐软件")).toHaveLength(2));

    fireEvent.click(screen.getAllByText("查看全部")[0]);
    expect(mocks.push).toHaveBeenCalledWith("/software/explore?sort=recommend");
  });
});
