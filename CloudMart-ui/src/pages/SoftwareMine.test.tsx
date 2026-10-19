import React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  listMySoftware: vi.fn(),
  getSoftwarePreferences: vi.fn(),
  getPublisherStatus: vi.fn(),
  listMyPublisherApplications: vi.fn(),
  listMySourceIssues: vi.fn(),
  getUpdatesUnreadCount: vi.fn(),
  listMyUpdates: vi.fn(),
  listMySubscriptions: vi.fn(),
  getSoftwareCardsByIds: vi.fn(),
  message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  searchParams: new URLSearchParams(),
}));

vi.mock("umi", () => ({
  history: { push: vi.fn() },
  useSearchParams: () => [mocks.searchParams, vi.fn()],
}));
vi.mock("@/stores/auth", () => ({
  useAuthStore: (selector: (state: { isAuthenticated: boolean; user: { id: string } }) => unknown) =>
    selector({ isAuthenticated: true, user: { id: "1" } }),
}));
vi.mock("@/api/software", () => ({
  deleteMySoftware: vi.fn(),
  getSoftwarePreferences: mocks.getSoftwarePreferences,
  getUpdatesUnreadCount: mocks.getUpdatesUnreadCount,
  listMySoftware: mocks.listMySoftware,
  listMySourceIssues: mocks.listMySourceIssues,
  listMySubscriptions: mocks.listMySubscriptions,
  listMyUpdates: mocks.listMyUpdates,
  markUpdatesRead: vi.fn(),
  readRecentBrowse: vi.fn(() => []),
  clearRecentBrowse: vi.fn(),
  getSoftwareCardsByIds: mocks.getSoftwareCardsByIds,
  subscribeSoftware: vi.fn(),
  unsubscribeSoftware: vi.fn(),
  updateSoftwarePreferences: vi.fn(),
  getPublisherStatus: mocks.getPublisherStatus,
  submitPublisherApplication: vi.fn(),
  listMyPublisherApplications: mocks.listMyPublisherApplications,
  cancelPublisherApplication: vi.fn(),
}));
vi.mock("@ant-design/icons", () => ({}));
vi.mock("antd", () => {
  const Box = ({ children }: React.PropsWithChildren) => <div>{children}</div>;
  const form = { setFieldsValue: vi.fn(), validateFields: vi.fn() };
  const Form = Object.assign(Box, { Item: Box, useForm: () => [form] });
  const Input = Object.assign((props: any) => <input {...props} />, {
    TextArea: (props: any) => <textarea {...props} />,
  });
  const ListItem = Object.assign(Box, {
    Meta: ({ title, description }: any) => <div>{title}{description}</div>,
  });
  const List = Object.assign(Box, { Item: ListItem });
  return {
    App: { useApp: () => ({ message: mocks.message }) },
    Button: ({ children, onClick, disabled }: any) =>
      <button onClick={onClick} disabled={disabled}>{children}</button>,
    Empty: ({ description, children }: any) => <div>{description}{children}</div>,
    Form,
    Input,
    List,
    Modal: Box,
    Pagination: () => null,
    Popconfirm: Box,
    Space: Box,
    Spin: Box,
    Switch: () => null,
    Tabs: ({ items, onChange }: any) => (
      <div>{items.map((item: any) => (
        <button key={item.key} onClick={() => onChange(item.key)}>{item.label}</button>
      ))}</div>
    ),
    Tag: Box,
    Typography: { Text: Box, Paragraph: Box },
  };
});

import SoftwareMine from "./SoftwareMine";

describe("SoftwareMine tabs", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.searchParams = new URLSearchParams();
    mocks.listMySoftware.mockResolvedValue({ data: { success: true, data: { records: [], total: 0 } } });
    mocks.getSoftwarePreferences.mockResolvedValue({ data: { data: { updateNotifications: true } } });
    mocks.getPublisherStatus.mockResolvedValue({ data: { data: { canPublish: true } } });
    mocks.listMyPublisherApplications.mockResolvedValue({ data: { data: { records: [] } } });
    mocks.listMySourceIssues.mockResolvedValue({ data: { data: [] } });
    mocks.getUpdatesUnreadCount.mockResolvedValue({ data: { data: 0 } });
    mocks.listMyUpdates.mockResolvedValue({ data: { data: { items: [], nextCursor: null, hasMore: false } } });
    mocks.listMySubscriptions.mockResolvedValue({ data: { data: [] } });
    mocks.getSoftwareCardsByIds.mockResolvedValue({ data: { data: [] } });
  });

  afterEach(cleanup);

  it("does not send UI-only recent and updates tabs to GET /software/mine", async () => {
    render(<SoftwareMine />);

    await waitFor(() => expect(mocks.listMySoftware).toHaveBeenCalled());
    fireEvent.click(screen.getByRole("button", { name: "最近浏览" }));
    fireEvent.click(screen.getByRole("button", { name: "更新" }));

    await waitFor(() => expect(mocks.listMyUpdates).toHaveBeenCalledTimes(1));
    expect(mocks.listMySoftware.mock.calls.flatMap(([params]) => params?.tab ?? []))
      .not.toContain("recent");
    expect(mocks.listMySoftware.mock.calls.flatMap(([params]) => params?.tab ?? []))
      .not.toContain("updates");
  });

  it("restores the software tab when browser history changes the URL", async () => {
    const view = render(<SoftwareMine />);
    await waitFor(() => expect(mocks.listMySoftware).toHaveBeenCalledWith({
      tab: "published",
      page: 1,
      size: 20,
    }));

    mocks.searchParams = new URLSearchParams("tab=favorites");
    view.rerender(<SoftwareMine />);

    await waitFor(() => expect(mocks.listMySoftware).toHaveBeenCalledWith({
      tab: "favorites",
      page: 1,
      size: 20,
    }));
  });
});
