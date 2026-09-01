package com.spring.aiprompt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.spring.aiprompt.dto.PromptDTO;
import com.spring.aiprompt.entity.Prompt;
import com.spring.aiprompt.vo.PromptVO;

import java.util.List;

/**
 * Prompt 业务接口
 */
public interface PromptService extends IService<Prompt> {

    /** 创建 Prompt：user_id 取当前登录人 */
    void create(PromptDTO dto);

    /**
     * 分页查询（统一入口）：
     * keyword 模糊匹配 title 或 description；categoryId 可选过滤；
     * onlyUserId 非空时只查该用户的 Prompt（/prompt/my 与 /admin/prompt/list 复用）
     */
    Page<PromptVO> pageList(long pageNum, long pageSize, String keyword, Long categoryId, Long onlyUserId);

    /** 详情：浏览次数原子 +1 */
    PromptVO detail(Long id);

    /** 编辑回填：仅查详情，不增加浏览次数 */
    PromptVO getForEdit(Long id);

    /** 修改：仅本人可改，否则 403 */
    void updatePrompt(Long id, PromptDTO dto);

    /** 删除：本人或管理员；物理删除并级联清理收藏、使用记录 */
    void deletePrompt(Long id);

    /** Prompt 列表 → PromptVO 列表（补充分类名/作者名/是否已收藏），收藏列表等场景复用 */
    List<PromptVO> toVOList(List<Prompt> prompts);
}
